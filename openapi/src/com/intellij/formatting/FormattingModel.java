/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.formatting;

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
   */
  void replaceWhiteSpace(TextRange textRange, String whiteSpace);

  /**
   * Indents every line except for the first in the specified text range representing a multiline block
   * by the specified amount.
   *
   * @param range  the text range to indent.
   * @param indent the amount by which every line should be indented.
   * @return the text range covering the block with added indents.
   */
  TextRange shiftIndentInsideRange(TextRange range, int indent);

  /**
   * Commits the changes made by the formatter to the document. Called after the formatter
   * has finished making all changes in a formatting operation.
   */
  void commitChanges();
}
