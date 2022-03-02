// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface ThrownExceptionInfo {
  void setType(PsiClassType type);

  @Nullable
  PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException;

  void updateFromMethod(PsiMethod method);

  int getOldIndex();
}
