package com.intellij.formatting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;

interface FormattingModelFactory {
  FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                  Block rootBlock,
                                                  CodeStyleSettings settings);
}
