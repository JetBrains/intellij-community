// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.model.psi.PsiExternalReferenceHost;
import org.jetbrains.annotations.Nullable;

public interface PsiLiteralValue extends PsiElement, PsiExternalReferenceHost {
  /**
   * Returns the value of the literal expression (an Integer for an integer constant, a String
   * for a string literal, and so on).
   *
   * @return the value of the expression, or null if the parsing of the literal failed.
   */
  @Nullable
  Object getValue();
}
