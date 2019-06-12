// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterDescriptor implements ItemToReplaceDescriptor {
  private final PsiTypeElement myTypeElement;
  private final PsiType myTypeToUse;

  public ParameterDescriptor(@NotNull PsiTypeElement typeElement, @NotNull PsiType typeToUse) {
    myTypeElement = typeElement;
    myTypeToUse = typeToUse;
  }

  @Nullable
  public static ParameterDescriptor createIfInaccessible(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    if (typeElement != null) {
      PsiType parameterType = typeElement.getType();
      if (!PsiReflectionAccessUtil.isAccessibleType(parameterType)) {
        return new ParameterDescriptor(typeElement, PsiReflectionAccessUtil.nearestAccessibleType(parameterType));
      }
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    myTypeElement.replace(elementFactory.createTypeElement(myTypeToUse));
  }
}
