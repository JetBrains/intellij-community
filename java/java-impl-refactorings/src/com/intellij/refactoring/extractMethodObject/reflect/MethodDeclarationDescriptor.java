// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodDeclarationDescriptor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(MethodDeclarationDescriptor.class);

  private final PsiMethod myMethod;
  private final PsiType myType;

  private MethodDeclarationDescriptor(@NotNull PsiMethod method, @NotNull PsiType type) {
    myMethod = method;
    myType = type;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    PsiType nearestAccessibleType = PsiReflectionAccessUtil.nearestAccessibleType(myType, callExpression);
    PsiTypeElement returnTypeElement = myMethod.getReturnTypeElement();

    if (returnTypeElement != null) {
      returnTypeElement.replace(elementFactory.createTypeElement(nearestAccessibleType));
    }

    if (myMethod.equals(callExpression.resolveMethod())) {
      PsiElement parent = callExpression.getParent();
      if (parent instanceof PsiLocalVariable) {
        ((PsiLocalVariable)parent).getTypeElement().replace(elementFactory.createTypeElement(nearestAccessibleType));
      }
      else {
        LOG.error("Unexpected psi parent type of call expression: " + parent.getClass().getCanonicalName());
      }
    }
  }

  public static @Nullable ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiMethod method, @NotNull PsiClass outerClass) {
    PsiType returnType = method.getReturnType();
    if (returnType != null &&
        !outerClass.equals(PsiTypesUtil.getPsiClass(returnType)) &&
        !PsiReflectionAccessUtil.isAccessibleType(returnType)) {
      return new MethodDeclarationDescriptor(method, returnType);
    }
    return null;
  }
}
