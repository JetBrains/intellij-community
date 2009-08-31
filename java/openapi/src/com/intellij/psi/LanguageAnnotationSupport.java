package com.intellij.psi;

import com.intellij.lang.LanguageExtension;

/**
 * @author Serega.Vasiliev
 */
public class LanguageAnnotationSupport extends LanguageExtension<PsiAnnotationSupport> {
  public static final LanguageAnnotationSupport INSTANCE = new LanguageAnnotationSupport();

  private LanguageAnnotationSupport() {
    super("com.intellij.annotationSupport");
  }
}
