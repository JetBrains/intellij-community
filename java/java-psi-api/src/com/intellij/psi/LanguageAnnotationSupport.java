// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.LanguageExtension;

/**
 * @author Serega.Vasiliev
 */
public final class LanguageAnnotationSupport extends LanguageExtension<PsiAnnotationSupport> {
  public static final LanguageAnnotationSupport INSTANCE = new LanguageAnnotationSupport();

  private LanguageAnnotationSupport() {
    super("com.intellij.annotationSupport");
  }
}
