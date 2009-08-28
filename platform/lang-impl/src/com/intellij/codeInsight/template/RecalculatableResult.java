package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface RecalculatableResult extends Result {
  void handleRecalc(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd);
}
