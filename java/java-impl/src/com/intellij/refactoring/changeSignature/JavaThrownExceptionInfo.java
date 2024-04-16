// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class JavaThrownExceptionInfo implements ThrownExceptionInfo {
  private final int oldIndex;
  private CanonicalTypes.Type myType;

  public JavaThrownExceptionInfo() {
    oldIndex = -1;
  }

  public JavaThrownExceptionInfo(int oldIndex) {
    this.oldIndex = oldIndex;
  }

  public JavaThrownExceptionInfo(int oldIndex, PsiClassType type) {
    this.oldIndex = oldIndex;
    setType(type);
  }

  //create identity mapping
  public static ThrownExceptionInfo[] extractExceptions(PsiMethod method) {
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new JavaThrownExceptionInfo(i, types[i]);
    }
    return result;
  }

  @Override
  public void setType(PsiClassType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  @Override
  public @Nullable PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (myType != null) {
      return myType.getType(context, manager);
    }
    else {
      return null;
    }
  }

  @Override
  public void updateFromMethod(PsiMethod method) {
    if (myType != null) return;
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    if (oldIndex >= 0) {
      setType(types[oldIndex]);
    }
  }

  @Override
  public int getOldIndex() {
    return oldIndex;
  }
}
