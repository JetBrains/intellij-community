package com.intellij.formatting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public interface FormattingModelBuilder {
  @NotNull FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings);
}
