package com.intellij.lang;

/**
 * @author max
 */
public interface Commenter {
  String getLineCommentPrefix();
  boolean isLineCommentPrefixOnZeroColumn();

  String getBlockCommentPrefix();
  String getBlockCommentSuffix();
}
