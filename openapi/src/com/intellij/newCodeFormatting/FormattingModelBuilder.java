package com.intellij.newCodeFormatting;

import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public interface FormattingModelBuilder {
  FormattingModel createModel(final PsiFile element, final CodeStyleSettings settings);
}
