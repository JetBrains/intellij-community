// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

@ApiStatus.Experimental
public final class AnalysisUastUtil {
  @Nullable
  public static UCallExpression getUCallExpression(@NotNull PsiElement element) {
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

  @Nullable
  public static PsiElement getMethodIdentifierSourcePsi(@NotNull UCallExpression callExpression) {
    UIdentifier methodIdentifier = callExpression.getMethodIdentifier();
    if (methodIdentifier == null) {
      return null;
    }
    return methodIdentifier.getSourcePsi();
  }

  @Nullable
  public static String getExpressionReturnTypePsiClassFqn(@NotNull UCallExpression expression) {
    return getTypeClassFqn(expression.getReturnType());
  }

  @Nullable
  public static PsiClass getTypePsiClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    return ((PsiClassType)type).rawType().resolve();
  }

  @Nullable
  public static String getExpressionReceiverTypeClassFqn(@NotNull UCallExpression expression) {
    return getTypeClassFqn(expression.getReceiverType());
  }

  @Nullable
  public static String getTypeClassFqn(@Nullable PsiType type) {
    if (type == null) return null;
    return type.getCanonicalText().replaceAll("<.*?>", ""); // workaround
    //TODO https://youtrack.jetbrains.com/issue/KT-25024
    //PsiClass psiClass = getTypePsiClass(type);
    //if (psiClass == null) return null;
    //return psiClass.getQualifiedName();
  }

  @Nullable
  public static String getCallableReferenceClassFqn(@NotNull UCallableReferenceExpression expression) {
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
}
