package com.intellij.lang;

import com.intellij.psi.PsiFile;

public interface LanguageExtension {
  boolean isRelevantForFile(final PsiFile psi);
  Language getLanguage();
}
