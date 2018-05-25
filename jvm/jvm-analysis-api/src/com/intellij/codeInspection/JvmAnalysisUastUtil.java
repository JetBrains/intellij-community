// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public final class JvmAnalysisUastUtil {
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
  public static String getExpressionReturnTypePsiClassFqnName(@NotNull UCallExpression expression) {
    PsiClass psiClass = getTypePsiClass(expression.getReturnType());
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  @Nullable
  public static PsiClass getTypePsiClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    return ((PsiClassType)type).rawType().resolve();
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
