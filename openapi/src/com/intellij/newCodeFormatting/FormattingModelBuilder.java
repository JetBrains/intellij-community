package com.intellij.newCodeFormatting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public interface FormattingModelBuilder {
  @NotNull FormattingModel createModel(final PsiFile element, final CodeStyleSettings settings);
}
