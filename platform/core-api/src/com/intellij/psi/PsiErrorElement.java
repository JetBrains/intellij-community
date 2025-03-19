// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a syntax error (for example, an invalid token) in custom language code.
 *
 * @see com.intellij.psi.util.PsiTreeUtil#hasErrorElements(PsiElement)
 * @see com.intellij.psi.util.PsiTreeUtilKt#hasErrorElementInRange(PsiFile, TextRange)
 * @see com.intellij.util.PsiErrorElementUtil
 */
public interface PsiErrorElement extends PsiElement {

  /**
   * @return the error description.
   */
  @NotNull @NlsContexts.DetailedDescription
  String getErrorDescription();
}