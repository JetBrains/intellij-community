// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

@ApiStatus.Internal
public final class AnalysisUastUtil {
  public static @Nullable UCallExpression getUCallExpression(@NotNull PsiElement element) {
    UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (callExpression == null) {
      return null;
    }
    // workaround for duplicate warnings on the same element
    if (callExpression.getSourcePsi() != element) {
      return null;
    }
    return callExpression;
  }

  public static @Nullable PsiElement getMethodIdentifierSourcePsi(@NotNull UCallExpression callExpression) {
    UIdentifier methodIdentifier = callExpression.getMethodIdentifier();
    if (methodIdentifier == null) {
      return null;
    }
    return methodIdentifier.getSourcePsi();
  }

  public static @Nullable String getExpressionReturnTypePsiClassFqn(@NotNull UCallExpression expression) {
    return getTypeClassFqn(expression.getReturnType());
  }

  public static @Nullable PsiClass getTypePsiClass(@Nullable PsiType type) {
    type = GenericsUtil.eliminateWildcards(type);
    if (!(type instanceof PsiClassType)) return null;
    return ((PsiClassType)type).rawType().resolve();
  }

  public static @Nullable String getExpressionReceiverTypeClassFqn(@NotNull UCallExpression expression) {
    return getTypeClassFqn(expression.getReceiverType());
  }

  public static @Nullable String getTypeClassFqn(@Nullable PsiType type) {
    if (type == null) return null;
    return type.getCanonicalText().replaceAll("<.*?>", ""); // workaround
    //TODO https://youtrack.jetbrains.com/issue/KT-25024
    //PsiClass psiClass = getTypePsiClass(type);
    //if (psiClass == null) return null;
    //return psiClass.getQualifiedName();
  }

  public static @Nullable String getCallableReferenceClassFqn(@NotNull UCallableReferenceExpression expression) {
    //TODO why getQualifierType() -> null for Java?
    String classFqn = getTypeClassFqn(expression.getQualifierType());
    if (classFqn != null) return classFqn;

    UExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null) return null;
    if (qualifierExpression instanceof UReferenceExpression) {
      PsiElement resolved = ((UReferenceExpression)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return ((PsiClass)resolved).getQualifiedName();
      }
      else if (resolved instanceof PsiVariable) {
        return getTypeClassFqn(((PsiVariable)resolved).getType());
      }
    }
    else if (qualifierExpression instanceof UThisExpression) {
      return getTypeClassFqn(qualifierExpression.getExpressionType());
    }
    return null;
  }

  //TODO use UastContext#isExpressionValueUsed ?
  public static boolean isExpressionResultValueUsed(@NotNull UCallExpression expression) {
    UElement currentParent = expression;
    while ((currentParent = currentParent.getUastParent()) != null) {
      ProgressManager.checkCanceled();

      if (currentParent instanceof UReturnExpression ||
          currentParent instanceof ULocalVariable ||
          currentParent instanceof UField ||
          currentParent instanceof UBinaryExpression ||
          currentParent instanceof UCallExpression) {
        return true;
      }

      // chain of calls
      if (currentParent instanceof UQualifiedReferenceExpression) {
        UExpression selector = ((UQualifiedReferenceExpression)currentParent).getSelector();
        if (selector instanceof UCallExpression && !selector.equals(expression)) {
          return true;
        }
      }

      if (currentParent instanceof UMethod ||
          currentParent instanceof UClass ||
          currentParent instanceof UFile) {
        break; // no need to go further if we already on method/class/file level; result is not used.
      }
    }

    //Kotlin expression-bodies (a workaround for KT-23557)
    if (currentParent instanceof UMethod && !(((UMethod)currentParent).getUastBody() instanceof UBlockExpression)) {
      return true;
    }

    return false;
  }

  public static PsiType getContainingMethodOrLambdaReturnType(UExpression expression) {
    UElement parent = expression.getUastParent();
    while (parent != null) {
      if (parent instanceof UMethod) {
        return ((UMethod)parent).getReturnType();
      }
      if (parent instanceof ULambdaExpression) {
        PsiType lambdaType = ((ULambdaExpression)parent).getBody().getExpressionType();
        if (lambdaType != null) return lambdaType;

        PsiType functionalInterfaceType = ((ULambdaExpression)parent).getFunctionalInterfaceType();
        if (functionalInterfaceType != null) {
          return LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        }
        return null;
      }
      if (parent instanceof UClass) {
        return null;
      }
      parent = parent.getUastParent();
    }
    return null;
  }


}
