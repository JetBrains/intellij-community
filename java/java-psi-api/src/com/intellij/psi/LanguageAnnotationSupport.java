// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.LanguageExtension;

public final class LanguageAnnotationSupport extends LanguageExtension<PsiAnnotationSupport> {
  public static final LanguageAnnotationSupport INSTANCE = new LanguageAnnotationSupport();

  private LanguageAnnotationSupport() {
    super("com.intellij.annotationSupport");
  }
}
