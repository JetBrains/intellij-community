// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author Vitaliy.Bibaev
 */
public class MethodReferenceReflectionAccessor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(MethodReferenceReflectionAccessor.class);

  private final PsiMethodReferenceExpression myExpression;

  public MethodReferenceReflectionAccessor(@NotNull PsiMethodReferenceExpression expression) {
    myExpression = expression;
  }

  @Nullable
  public static MethodReferenceReflectionAccessor createIfInaccessible(@NotNull PsiReferenceExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      PsiElement resolvedElement = expression.resolve();
      if (resolvedElement instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolvedElement;
        if (!PsiReflectionAccessUtil.isAccessibleMember(method)) {
          return new MethodReferenceReflectionAccessor((PsiMethodReferenceExpression)expression);
        }
      }
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(myExpression, false, true);
    PsiElement lambdaBody = lambda == null ? null : lambda.getBody();
    if (lambdaBody != null) {
      if (lambdaBody instanceof PsiNewExpression) {
        ConstructorReflectionAccessor constructorDescriptor =
          ConstructorReflectionAccessor.createIfInaccessible((PsiNewExpression)lambdaBody);
        if (constructorDescriptor != null) {
          constructorDescriptor.replace(outerClass, elementFactory, callExpression);
        }
        else {
          LOG.warn("Inaccessible constructor not found. Method reference: " + myExpression.getText());
        }
      }
      else if (lambdaBody instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)lambdaBody;
        MethodReflectionAccessor accessor = MethodReflectionAccessor.createIfInaccessible(outerClass, methodCallExpression);
        if (accessor != null) {
          accessor.replace(outerClass, elementFactory, callExpression);
        }
        else {
          LOG.warn("Could not resolve method from expression: " + methodCallExpression.getText());
        }
      }
      else {
        LOG.warn("Unexpected type of lambda body: " + lambdaBody.getClass().getCanonicalName());
      }
    }
    else {
      LOG.warn("Could not replace method reference with lambda: " + myExpression.getText());
    }
  }
}
