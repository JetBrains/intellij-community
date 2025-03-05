
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Defines support for "Enter within comments" actions in a custom language.
 *
 * @see CodeDocumentationAwareCommenterEx
 */
public interface CodeDocumentationAwareCommenter extends Commenter {
  /**
   * Returns the type of the line comment in the language,
   * or {@code null} if the language does not support line comments.
   * It is assumed that the line comment prefix is not {@code null} when the line comment type is not {@code null}.
   *
   * @return the line comment type.
   */
  @Nullable
  IElementType getLineCommentTokenType();

  default @Unmodifiable @NotNull List<IElementType> getLineCommentTokenTypes() {
    return ContainerUtil.createMaybeSingletonList(getLineCommentTokenType());
  }

  /**
   * Returns the type of the block comment in the language,
   * or {@code null} if the language does not support block comments.
   * It is assumed that the block comment prefix is not {@code null} when the block comment type is not {@code null}.
   *
   * @return the block comment type.
   */
  @Nullable
  IElementType getBlockCommentTokenType();

  /**
   * Returns the type of the documentation comment token in the language,
   * or {@code null} if the language does not support documentation comments.
   * It is assumed that the documentation comment prefix is not {@code null} when the documentation comment type is not {@code null}.
   *
   * @return the documentation comment type.
   */
  @Nullable
  IElementType getDocumentationCommentTokenType();

  /**
   * Returns the string which starts documentation comment in the language, or {@code null} if the language
   * does not support documentation comments.
   *
   * @return the documentation comment text, or {@code null}.
   */
  @Nullable String getDocumentationCommentPrefix();

  /**
   * Returns the string which prefixes documentation line comment in the language, or {@code null} if the language
   * does not support documentation comments.
   *
   * @return the line comment text, or {@code null}.
   */
  @Nullable String getDocumentationCommentLinePrefix();

  /**
   * Returns the string which ends documentation comment in the language, or {@code null} if the language
   * does not support documentation comments.
   *
   * @return the documentation comment suffix text, or {@code null}.
   */
  @Nullable String getDocumentationCommentSuffix();

  boolean isDocumentationComment(PsiComment element);
}
