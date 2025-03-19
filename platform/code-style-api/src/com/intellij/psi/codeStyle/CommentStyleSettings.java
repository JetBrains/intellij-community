// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.ApiStatus;


/**
 * Code style settings related to comments.
 */
@ApiStatus.Experimental
public interface CommentStyleSettings {

  /**
   * Specifies when should be placed the line comment marker
   * when this line comment is added automatically, or when
   * a user asked the Idea to comment one or several lines out.
   *
   * @return {@code true} means that the marker should be placed in
   * the first position of the line,
   * {@code false} means that the marker should be indented
   */
  boolean isLineCommentInTheFirstColumn();

  /**
   * Specifies whether the line comment marker should be
   * followed with a space.
   *
   * @return {@code true} — with space, {@code false} — without space
   */
  boolean isLineCommentFollowedWithSpace();

  /**
   * Specifies whether the block comment markers should have additional
   * spaces (after the opening marker and before the closing one).
   *
   * @return {@code true} — with spaces, {@code false} — without spaces
   */
  boolean isBlockCommentIncludesSpace();

}
