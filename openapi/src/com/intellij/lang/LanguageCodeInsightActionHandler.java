package com.intellij.lang;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

public interface LanguageCodeInsightActionHandler extends CodeInsightActionHandler {
  boolean isValidFor(Editor editor, PsiFile file);
}
