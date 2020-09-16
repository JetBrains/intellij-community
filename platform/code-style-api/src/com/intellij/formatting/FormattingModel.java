/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the formatting model for a file. A formatting model defines how a file is broken
 * into non-whitespace blocks and different types of whitespace (alignment, indents and wraps)
 * between them.
 * <p>
 * Typically, a plugin does not need to provide a complete FormattingModel implementation -
 * it can either use a complete implementation provided by
 * {@link FormattingModelProvider#createFormattingModelForPsiFile(com.intellij.psi.PsiFile, Block, com.intellij.psi.codeStyle.CodeStyleSettings)}
 * or implement the necessary extra features and delegate the rest to the factory implementation.
 *
 * @see FormattingModelBuilder#createModel(com.intellij.psi.PsiElement, com.intellij.psi.codeStyle.CodeStyleSettings)
 * @see FormattingModelProvider#createFormattingModelForPsiFile(com.intellij.psi.PsiFile, Block, com.intellij.psi.codeStyle.CodeStyleSettings)
 */

public interface FormattingModel {
  /**
   * Returns the root block of the formatting model. The root block corresponds to the
   * top-level element passed to
   * {@link FormattingModelBuilder#createModel(com.intellij.psi.PsiElement, com.intellij.psi.codeStyle.CodeStyleSettings)}.
   *
   * @return the root block of the model.
   */
  @NotNull
  Block getRootBlock();

  /**
   * Returns the formatting document model, which allows the formatter to access information about
   * the document containing the text to be formatted.
   *
   * @return the formatting document model.
   */
  @NotNull
  FormattingDocumentModel getDocumentModel();

  /**
   * Replaces the contents of the specified text range in the document with the specified text
   * string consisting of whitespace characters. If necessary, other characters may be inserted
   * in addition to the passed whitespace (for example, \ characters for breaking lines in
   * languages like Python).
   *
   * @param textRange  the text range to replace with whitespace.
   * @param whiteSpace the whitespace to replace with.
   * @return new white space text range
   */
  TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace);

  /**
   * Indents every line except for the first in the specified text range representing a multiline block
   * by the specified amount.
   *
   *
   * @param node the owner of the text range, if defined.
   * @param range  the text range to indent.
   * @param indent the amount by which every line should be indented.
   * @return the text range covering the block with added indents.
   */
  TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent);

  /**
   * Commits the changes made by the formatter to the document. Called after the formatter
   * has finished making all changes in a formatting operation.
   */
  void commitChanges();
}
