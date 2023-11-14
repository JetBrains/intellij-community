// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

/**
 * Code style settings related to comments.
 */
public interface CommentStyleSettings {

  boolean isLineCommentInTheFirstColumn();

  boolean isLineCommentFollowedWithSpace();

  boolean isBlockCommentIncludesSpace();

}
