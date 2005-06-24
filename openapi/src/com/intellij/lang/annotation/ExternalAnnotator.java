package com.intellij.lang.annotation;

import com.intellij.psi.PsiFile;

/**
 * @author ven
 */
public interface ExternalAnnotator {
  void annotate(PsiFile file, AnnotationHolder holder);
}
