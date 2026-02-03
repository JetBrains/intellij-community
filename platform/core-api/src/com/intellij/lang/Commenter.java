// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Defines the support for "Comment with Line Comment" and "Comment with Block Comment"
 * actions in a custom language.
 *
 * @see CodeDocumentationAwareCommenter
 * @see com.intellij.codeInsight.generation.CommenterWithLineSuffix
 * @see com.intellij.codeInsight.generation.EscapingCommenter
 * @see com.intellij.codeInsight.generation.IndentedCommenter
 * @see com.intellij.codeInsight.generation.SelfManagingCommenter
 */
public interface Commenter {
  /**
   * Returns the string that prefixes a line comment in the language, or null if the language
   * does not support line comments. If the language supports several prefixes for line comments,
   * only one of them (the most recommended to use) is returned. Use {@link #getLineCommentPrefixes()}
   * to get all supported line comment prefixes.
   *
   * @return the line comment text, or null.
   */
  @Nullable
  String getLineCommentPrefix();

  /**
   * Returns the list of strings that prefix line comments in the language, or empty list
   * if the language does not support line comments.
   *
   * @return the list of line comment prefixes
   */
  default @Unmodifiable @NotNull List<String> getLineCommentPrefixes() {
    return ContainerUtil.createMaybeSingletonList(getLineCommentPrefix());
  }

  /**
   * Returns the string which marks the beginning of a block comment in the language,
   * or null if the language does not support block comments.
   *
   * @return the block comment start text, or null.
   */
  @Nullable
  String getBlockCommentPrefix();

  /**
   * Returns the string which marks the end of a block comment in the language,
   * or null if the language does not support block comments.
   *
   * @return the block comment end text, or null.
   */
  @Nullable
  String getBlockCommentSuffix();

  /**
   * Returns the string which marks the commented beginning of a block comment in the language,
   * or null if the language does not support block comments.
   *
   * @return the commented block comment start text, or null.
   */
  @Nullable
  String getCommentedBlockCommentPrefix();

  /**
   * Returns the string which marks the commented end of a block comment in the language,
   * or null if the language does not support block comments.
   *
   * @return the commented block comment end text, or null.
   */
  @Nullable
  String getCommentedBlockCommentSuffix();

  /**
   * Some indentation-based languages require the block comment prefix and suffix to be placed at the
   * start of the line. This method allows to specify that the selection must be extended to match
   * only full lines. This way prefix and suffix will be inserted at the beginning and at the end of the line
   *
   * @return whether the selection should be extended to match only full lines
   */
  default boolean blockCommentRequiresFullLineSelection() {
    return false;
  }
}
