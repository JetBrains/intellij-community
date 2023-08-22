// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to build a formatting model for a file in the language, or
 * for a portion of a file.
 * A formatting model defines how a file is broken into non-whitespace blocks and different
 * types of whitespace (alignment, indents and wraps) between them.
 * <p>For certain aspects of the custom formatting to work properly, it is recommended to use TokenType.WHITE_SPACE
 * as the language's whitespace tokens. See {@link com.intellij.lang.ParserDefinition}
 *
 * @apiNote in case you getting a {@link StackOverflowError}, with your builder, most likely you haven't implemented any model building
 * methods. Please, implement {@link #createModel(FormattingContext)}
 * @see com.intellij.lang.LanguageFormatting
 * @see FormattingModelProvider#createFormattingModelForPsiFile(PsiFile, Block, CodeStyleSettings)
 */
public interface FormattingModelBuilder {

  /**
   * Requests building the formatting model for a section of the file containing
   * the specified PSI element and its children.
   *
   * @return the formatting model for the file.
   * @see FormattingContext
   */
  default @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    return createModel(formattingContext.getPsiElement(),
                       formattingContext.getFormattingRange(),
                       formattingContext.getCodeStyleSettings(),
                       formattingContext.getFormattingMode());
  }

  /**
   * Returns the TextRange which should be processed by the formatter in order to detect proper indent options.
   *
   * @param file            the file in which the line break is inserted.
   * @param offset          the line break offset.
   * @param elementAtOffset the parameter at {@code offset}
   * @return the range to reformat, or null if the default range should be used
   */
  default @Nullable TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  /**
   * @deprecated use {@link #createModel(FormattingContext)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull FormattingModel createModel(final @NotNull PsiElement element,
                                               final @NotNull TextRange range,
                                               final @NotNull CodeStyleSettings settings,
                                               final @NotNull FormattingMode mode) {
    return createModel(element, settings, mode); // just for compatibility with old implementations
  }

  /**
   * @deprecated use {@link #createModel(FormattingContext)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull FormattingModel createModel(final @NotNull PsiElement element,
                                               final @NotNull CodeStyleSettings settings,
                                               @NotNull FormattingMode mode) {
    return createModel(element, settings);
  }

  /**
   * @deprecated use {@link #createModel(FormattingContext)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    return createModel(FormattingContext.create(element, settings));
  }
}
