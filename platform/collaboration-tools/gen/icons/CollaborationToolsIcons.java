// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  /** 16x16 */ public static final @NotNull Icon AddEmoji = load("icons/addEmoji.svg", -1498019086, 0);
  /** 16x16 */ public static final @NotNull Icon AddEmojiHovered = load("icons/addEmojiHovered.svg", -203239088, 0);
  /** 16x16 */ public static final @NotNull Icon Comment = load("icons/comment.svg", -744086647, 0);
  /** 16x16 */ public static final @NotNull Icon Delete = load("icons/delete.svg", -103361912, 0);
  /** 16x16 */ public static final @NotNull Icon DeleteHovered = load("icons/deleteHovered.svg", 540375735, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestClosed = load("icons/pullRequestClosed.svg", -1621538976, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestOpen = load("icons/pullRequestOpen.svg", 1836395508, 0);

  public static final class Review {
    /** 16x16 */ public static final @NotNull Icon Branch = load("icons/review/branch.svg", -1729430544, 2);
    /** 16x16 */ public static final @NotNull Icon CommentHovered = load("icons/review/commentHovered.svg", 1490465624, 0);
    /** 16x16 */ public static final @NotNull Icon CommentReadMany = load("icons/review/commentReadMany.svg", -1322485699, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnread = load("icons/review/commentUnread.svg", -1937473850, 0);
    /** 16x16 */ public static final @NotNull Icon CommentUnreadMany = load("icons/review/commentUnreadMany.svg", -1694628613, 2);
    /** 16x16 */ public static final @NotNull Icon CommentUnresolved = load("icons/review/commentUnresolved.svg", -3756667, 0);
    /** 16x16 */ public static final @NotNull Icon FileUnread = load("icons/review/fileUnread.svg", 1594372685, 0);
    /** 16x16 */ public static final @NotNull Icon NonMergeable = load("icons/review/nonMergeable.svg", -107566367, 0);
  }

  /** 16x16 */ public static final @NotNull Icon Send = load("icons/send.svg", 1555154758, 0);
  /** 16x16 */ public static final @NotNull Icon SendHovered = load("icons/sendHovered.svg", -2005907271, 0);
}
