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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * Allows plugins to create an instance of the standard {@link FormattingModel} implementation.
 *
 * @see FormattingModelBuilder#createModel(com.intellij.psi.PsiElement, com.intellij.psi.codeStyle.CodeStyleSettings)
 */

public class FormattingModelProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormattingModelProvider");
  private static FormattingModelFactory myFactory;

  static void setFactory(FormattingModelFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  /**
   * Creates an instance of the standard formatting model implementation for the specified file.
   *
   * @param file      the file containing the text to format.
   * @param rootBlock the root block of the formatting model.
   * @param settings  the code style settings used for formatting.
   * @return the formatting model instance.
   */

  public static FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                                    Block rootBlock,
                                                                    CodeStyleSettings settings){
    return myFactory.createFormattingModelForPsiFile(file, rootBlock, settings);
  }

}
