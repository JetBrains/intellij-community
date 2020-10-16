package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface IndentsPassFilter {
    LanguageExtension<IndentsPassFilter> EXTENSION_POINT = new LanguageExtension<>("com.intellij.daemon.indentsPassFilter");

    boolean hasCustomIndentPass(@NotNull PsiFile file);
}
