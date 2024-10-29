// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class CollaborationToolsIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, CollaborationToolsIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, CollaborationToolsIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon AddEmoji = load("icons/expui/addEmoji.svg", "icons/addEmoji.svg", -1387552992, 0);
  /** 16x16 */ public static final @NotNull Icon AddEmojiHovered = load("icons/addEmojiHovered.svg", 1679333898, 0);
  /** 16x16 */ public static final @NotNull Icon Comment = load("icons/expui/comment.svg", "icons/comment.svg", 585242443, 0);
  /** 16x16 */ public static final @NotNull Icon Delete = load("icons/delete.svg", -26771446, 0);
  /** 16x16 */ public static final @NotNull Icon DeleteHovered = load("icons/deleteHovered.svg", -505177250, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestClosed = load("icons/pullRequestClosed.svg", 1915762689, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestOpen = load("icons/pullRequestOpen.svg", 410240917, 0);

  public static final class Review {
    /** 16x16 */ public static final @NotNull Icon BranchCurrent = load("icons/review/branchCurrent.svg", -1058941099, 0);
    /** 16x16 */ public static final @NotNull Icon CommentHovered = load("icons/review/commentHovered.svg", 2035016330, 0);
    /** 16x16 */ public static final @NotNull Icon CommentReadMany = load("icons/review/commentReadMany.svg", -653630789, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnread = load("icons/expui/review/commentUnread.svg", "icons/review/commentUnread.svg", 1972289510, 0);
    /** 16x16 */ public static final @NotNull Icon CommentUnreadMany = load("icons/review/commentUnreadMany.svg", 2062007617, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnresolved = load("icons/expui/comment.svg", "icons/review/commentUnresolved.svg", 75435315, 0);
    /** 16x16 */ public static final @NotNull Icon DefaultAvatar = load("icons/review/defaultAvatar.svg", -5156320, 0);
    /** 16x16 */ public static final @NotNull Icon FileUnread = load("icons/expui/review/fileUnread.svg", "icons/review/fileUnread.svg", 776396787, 0);
    /** 16x16 */ public static final @NotNull Icon NonMergeable = load("icons/review/nonMergeable.svg", 717316511, 0);

    /** @deprecated use DvcsImplIcons.BranchLabel instead */
    @SuppressWarnings("unused")
    @Deprecated
    public static final @NotNull Icon Branch = load("icons/review/branch.svg", 0, 0);
  }

  /** 16x16 */ public static final @NotNull Icon Send = load("icons/send.svg", 1522034072, 0);
  /** 16x16 */ public static final @NotNull Icon SendHovered = load("icons/sendHovered.svg", -698602769, 0);

  /** @deprecated use CollaborationToolsIcons.Review.FileUnread instead */
  @SuppressWarnings("unused")
  @Deprecated
  public static final @NotNull Icon FileUnread = load("icons/fileUnread.svg", 0, 0);
}
