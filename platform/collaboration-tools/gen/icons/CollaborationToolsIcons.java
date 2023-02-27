// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  /** 16x16 */ public static final @NotNull Icon AddEmoji = load("icons/addEmoji.svg", -148669730, 0);
  /** 16x16 */ public static final @NotNull Icon AddEmojiHovered = load("icons/addEmojiHovered.svg", 255095119, 0);
  /** 16x16 */ public static final @NotNull Icon Comment = load("icons/comment.svg", 434693468, 0);
  /** 16x16 */ public static final @NotNull Icon Delete = load("icons/delete.svg", -1322057667, 0);
  /** 16x16 */ public static final @NotNull Icon DeleteHovered = load("icons/deleteHovered.svg", 1457380528, 0);
  /** 16x16 */ public static final @NotNull Icon FileUnread = load("icons/fileUnread.svg", -1208661777, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestClosed = load("icons/pullRequestClosed.svg", 410361929, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestOpen = load("icons/pullRequestOpen.svg", -1114527288, 0);

  public static final class Review {
    /** 16x16 */ public static final @NotNull Icon Branch = load("icons/review/branch.svg", -843451441, 2);
    /** 16x16 */ public static final @NotNull Icon CommentHovered = load("icons/review/commentHovered.svg", 1252739370, 0);
    /** 16x16 */ public static final @NotNull Icon CommentReadMany = load("icons/review/commentReadMany.svg", -1189080140, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnread = load("icons/review/commentUnread.svg", -144577554, 0);
    /** 16x16 */ public static final @NotNull Icon CommentUnreadMany = load("icons/review/commentUnreadMany.svg", -1322379145, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnresolved = load("icons/review/commentUnresolved.svg", 1519158026, 0);
    /** 16x16 */ public static final @NotNull Icon DefaultAvatar = load("icons/review/defaultAvatar.svg", 2035996389, 0);
    /** 16x16 */ public static final @NotNull Icon NonMergeable = load("icons/review/nonMergeable.svg", -379994552, 0);
  }

  /** 16x16 */ public static final @NotNull Icon Send = load("icons/send.svg", 244823001, 0);
  /** 16x16 */ public static final @NotNull Icon SendHovered = load("icons/sendHovered.svg", -1994712541, 0);
}
