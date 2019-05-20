// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterDescriptor implements ItemToReplaceDescriptor {
  private final PsiParameter myParameter;
  private final String myName;

  public ParameterDescriptor(@NotNull PsiParameter parameter, @NotNull String name) {
    myParameter = parameter;
    myName = name;
  }

  @Nullable
  public static ParameterDescriptor createIfInaccessible(@NotNull PsiParameter parameter) {
    String parameterName = parameter.getName();
    if (!PsiReflectionAccessUtil.isAccessibleType(parameter.getType()) && parameterName != null) {
      return new ParameterDescriptor(parameter, parameterName);
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    myParameter.replace(elementFactory.createParameter(myName, PsiReflectionAccessUtil.nearestAccessibleType(myParameter.getType())));
  }
}
