// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @deprecated Use {@link JavaCompletionUtil#AFTER_DOT} instead.
 */
@Deprecated
public final class JavaKeywordCompletion {
  /**
   * @deprecated Use {@link JavaCompletionUtil#AFTER_DOT} instead.
   */
  @Deprecated
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");
}
