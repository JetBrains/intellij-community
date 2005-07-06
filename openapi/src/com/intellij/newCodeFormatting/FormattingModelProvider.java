package com.intellij.newCodeFormatting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class FormattingModelProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.newCodeFormatting.FormattingModelProvider");
  private static FormattingModelFactory myFactory;

  static void setFactory(FormattingModelFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                                    Block rootBlock,
                                                                    CodeStyleSettings settings){
    return myFactory.createFormattingModelForPsiFile(file, rootBlock, settings);
  }

}
