package com.intellij.lang;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.tree.IElementType;

/**
 * Defines support for "Enter within comments" actions in a custom language.
 * @author Maxim.Mossienko
 * @see Language#getCommenter()
 */
public interface CodeDocumentationAwareCommenter extends Commenter {
  /**
   * Returns the type of the line comment in the language,
   * or null if the language does not support line comments.
   * It is assumed that line comment prefix is not null when line comment type is not null.
   * @return the line comment type.
   */
  @Nullable
  IElementType getLineCommentTokenType();

  /**
   * Returns the type of the block comment in the language,
   * or null if the language does not support block comments.
   * It is assumed that block comment prefix is not null when block comment type is not null.
   * @return the line block comment type.
   */
  @Nullable
  IElementType getBlockCommentTokenType();

  /**
   * Returns the type of the documentation comment in the language,
   * or null if the language does not support documentation comments.
   * It is assumed that documentation comment prefix is not null when documentation comment type is not null.
   * @return the line block comment type.
   */
  @Nullable
  IElementType getDocumentationCommentTokenType();

  /**
   * Returns the string which starts documentation comment in the language, or null if the language
   * does not support documentation comments.
   * @return the documentation comment text, or null.
   */
  @Nullable String getDocumentationCommentPrefix();

  /**
   * Returns the string which prefixes documentation line comment in the language, or null if the language
   * does not support documentation comments.
   * @return the line comment text, or null.
   */
  @Nullable String getDocumentationCommentLinePrefix();

  /**
   * Returns the string which ends documentation comment in the language, or null if the language
   * does not support documentation comments.
   * @return the documentation comment end text, or null.
   */
  @Nullable String getDocumentationCommentSuffix();
}
