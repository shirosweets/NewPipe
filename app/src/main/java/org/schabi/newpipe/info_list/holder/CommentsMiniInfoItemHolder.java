package org.schabi.newpipe.info_list.holder;

import static android.text.TextUtils.isEmpty;

import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.text.CommentTextOnTouchListener;
import org.schabi.newpipe.util.text.TextEllipsizer;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class CommentsMiniInfoItemHolder extends InfoItemHolder {
    private static final String TAG = "CommentsMiniIIHolder";

    private static final int COMMENT_DEFAULT_LINES = 2;
    private final int commentHorizontalPadding;
    private final int commentVerticalPadding;

    private final RelativeLayout itemRoot;
    private final ImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemPublishedTime;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Description commentText;
    @Nullable private StreamingService streamService;
    @Nullable private String streamUrl;

    @NonNull private final TextEllipsizer textEllipsizer;

    CommentsMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                               final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemRoot = itemView.findViewById(R.id.itemRoot);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemPublishedTime = itemView.findViewById(R.id.itemPublishedTime);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);

        commentHorizontalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding);
        commentVerticalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding);

        textEllipsizer = new TextEllipsizer(itemContentView, COMMENT_DEFAULT_LINES, streamService);
        textEllipsizer.setStateChangeListener(isEllipsized -> determineMovementMethod());
    }

    public CommentsMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                      final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_comments_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof CommentsInfoItem)) {
            return;
        }
        final CommentsInfoItem item = (CommentsInfoItem) infoItem;
        textEllipsizer.setContent(item.getCommentText());

        PicassoHelper.loadAvatar(item.getUploaderAvatarUrl()).into(itemThumbnailView);
        if (PicassoHelper.getShouldLoadImages()) {
            itemThumbnailView.setVisibility(View.VISIBLE);
            itemRoot.setPadding(commentVerticalPadding, commentVerticalPadding,
                    commentVerticalPadding, commentVerticalPadding);
        } else {
            itemThumbnailView.setVisibility(View.GONE);
            itemRoot.setPadding(commentHorizontalPadding, commentVerticalPadding,
                    commentHorizontalPadding, commentVerticalPadding);
        }


        itemThumbnailView.setOnClickListener(view -> openCommentAuthor(item));

        try {
            streamService = NewPipe.getService(item.getServiceId());
        } catch (final ExtractionException e) {
            // should never happen
            ErrorUtil.showUiErrorSnackbar(itemBuilder.getContext(), "Getting StreamingService", e);
            Log.w(TAG, "Cannot obtain service from comment service id, defaulting to YouTube", e);
            streamService = ServiceList.YouTube;
        }
        streamUrl = item.getUrl();
        commentText = item.getCommentText();

        //noinspection ConstantConditions
        textEllipsizer.setStreamingService(streamService);
        textEllipsizer.setContent(item.getCommentText());
        textEllipsizer.setStreamUrl(item.getUrl());
        textEllipsizer.setStateChangeListener(isEllipsized -> {
            if (Boolean.TRUE.equals(isEllipsized)) {
                denyLinkFocus();
            } else {
                determineMovementMethod();
            }
        });
        textEllipsizer.ellipsize();

        //noinspection ClickableViewAccessibility
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.INSTANCE);

        if (item.getLikeCount() >= 0) {
            itemLikesCountView.setText(
                    Localization.shortCount(
                            itemBuilder.getContext(),
                            item.getLikeCount()));
        } else {
            itemLikesCountView.setText("-");
        }

        if (item.getUploadDate() != null) {
            itemPublishedTime.setText(Localization.relativeTime(item.getUploadDate()
                    .offsetDateTime()));
        } else {
            itemPublishedTime.setText(item.getTextualUploadDate());
        }

        itemView.setOnClickListener(view -> {
            textEllipsizer.toggle();
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (DeviceUtils.isTv(itemBuilder.getContext())) {
                openCommentAuthor(item);
            } else {
                final CharSequence text = itemContentView.getText();
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.getContext(), text.toString());
                }
            }
            return true;
        });
    }

    private void openCommentAuthor(final CommentsInfoItem item) {
        if (isEmpty(item.getUploaderUrl())) {
            return;
        }
        final AppCompatActivity activity = (AppCompatActivity) itemBuilder.getContext();
        try {
            NavigationHelper.openChannelFragment(
                    activity.getSupportFragmentManager(),
                    item.getServiceId(),
                    item.getUploaderUrl(),
                    item.getUploaderName());
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(activity, "Opening channel fragment", e);
        }
    }

    private void allowLinkFocus() {
        itemContentView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void denyLinkFocus() {
        itemContentView.setMovementMethod(null);
    }

    private boolean shouldFocusLinks() {
        if (itemView.isInTouchMode()) {
            return false;
        }

        final URLSpan[] urls = itemContentView.getUrls();

        return urls != null && urls.length != 0;
    }

    private void determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }
}
