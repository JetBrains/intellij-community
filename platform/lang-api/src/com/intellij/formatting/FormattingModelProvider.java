// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugins to create an instance of the standard {@link FormattingModel} implementation.
 *
 * @see FormattingModelBuilder#createModel(com.intellij.psi.PsiElement, CodeStyleSettings)
 */
public final class FormattingModelProvider {
  /**
   * Creates an instance of the standard formatting model implementation for the specified file.
   *
   * @param file      the file containing the text to format.
   * @param rootBlock the root block of the formatting model.
   * @param settings  the code style settings used for formatting.
   * @return the formatting model instance.
   */

  public static FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                                @NotNull Block rootBlock,
                                                                CodeStyleSettings settings){
    return Formatter.getInstance().createFormattingModelForPsiFile(file, rootBlock, settings);
  }
}
