
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
   * Returns the type of the <b>block</b> documentation comment token in the language,
   * or {@code null} if the language does not support <b>block</b> documentation comments.
   * It is assumed that {@link #getDocumentationCommentPrefix()} does not return {@code null} when the documentation comment type is not {@code null}.
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

  @Nullable
  default IElementType getDocumentationLineCommentTokenType() {
    return null;
  }

  /// Returns the types of the documentation **line** comment token in the language,
  /// or `null` if the language does not support documentation **line** comments.
  ///
  /// It is assumed that [#getDocumentationLineCommentPrefixes()] does not return `null` when the documentation comment type is not `null`.
  /// Implementations are expected to have [List]s of the same length between this function and [#getDocumentationLineCommentPrefixes()]
  @Nullable
  default @Unmodifiable List<IElementType> getDocumentationLineCommentTokenTypes() {
    return ContainerUtil.createMaybeSingletonList(getDocumentationLineCommentTokenType());
  }

  /// Variant of [getDocumentationLineCommentPrefixes] that returns a single prefix
  @Nullable
  default String getDocumentationLineCommentPrefix() {
    return null;
  }

  /// Returns the prefixes of the **line** documentation comment in the language,
  /// or `null` if the language does not support **line** documentation comments.
  ///
  /// It is assumed that [#getDocumentationLineCommentTokenTypes()] does not return `null` when the documentation comment type is not `null`.
  /// Implementations are expected to have [List]s of the same length between this function and [#getDocumentationLineCommentTokenTypes()]
  @Nullable
  default @Unmodifiable List<String> getDocumentationLineCommentPrefixes() {
    return ContainerUtil.createMaybeSingletonList(getDocumentationLineCommentPrefix());
  }

  boolean isDocumentationComment(PsiComment element);

  /// @return `true` if the comment is a documentation **line** comment
  default boolean isDocumentationLineComment(PsiComment element) {
    return false;
  }
}
