// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiTypeParameter;

public class Existing implements TypeParameterInfo {
  private final int myOldParameterIndex;

  public Existing(int oldIndex) {
    myOldParameterIndex = oldIndex;
  }

  public int getParameterIndex() {
    return myOldParameterIndex;
  }

  @Override
  public String getName(PsiTypeParameter[] parameters) {
    return parameters[myOldParameterIndex].getName();
  }

  @Override
  public PsiTypeParameter getTypeParameter(PsiTypeParameter[] parameters, Project project) {
    return parameters[myOldParameterIndex];
  }
}
