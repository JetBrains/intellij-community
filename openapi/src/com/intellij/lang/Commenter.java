package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public interface Commenter {
  IElementType getLineCommentToken();
  String getLineCommentPrefix();
  boolean isLineCommentPrefixOnZeroColumn();

  IElementType getBlockCommentToken();
  String getBlockCommentPrefix();
  String getBlockCommentSuffix();
}
