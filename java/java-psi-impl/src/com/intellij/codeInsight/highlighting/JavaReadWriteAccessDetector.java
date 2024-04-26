// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;


public class JavaReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(final @NotNull PsiElement element) {
    return element instanceof PsiVariable && !(element instanceof ImplicitVariable)
           || element instanceof PsiClass
           || element instanceof PsiAnnotationMethod && !(element instanceof PsiCompiledElement);
  }

  @Override
  public boolean isDeclarationWriteAccess(final @NotNull PsiElement element) {
    if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
      return true;
    }
    if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiForeachStatement) {
      return true;
    }
    return false;
  }

  @Override
  public @NotNull Access getReferenceAccess(final @NotNull PsiElement referencedElement, final @NotNull PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public @NotNull Access getExpressionAccess(final @NotNull PsiElement expression) {
    if (!(expression instanceof PsiExpression)) {
      if (expression instanceof PsiNameValuePair || expression instanceof PsiIdentifier) {
        return Access.Write;
      }
      return Access.Read;
    }
    PsiExpression expr = (PsiExpression) expression;
    boolean readAccess = PsiUtil.isAccessedForReading(expr);
    boolean writeAccess = PsiUtil.isAccessedForWriting(expr);
    if (!writeAccess && expr instanceof PsiReferenceExpression) {
      //when searching usages of fields, should show all found setters as a "only write usage"
      PsiElement actualReferee = ((PsiReferenceExpression) expr).resolve();
      if (actualReferee instanceof PsiMethod && PropertyUtilBase.isSimplePropertySetter((PsiMethod)actualReferee)) {
        writeAccess = true;
        readAccess = false;
      }
    }
    if (writeAccess && readAccess) return Access.ReadWrite;
    return writeAccess ? Access.Write : Access.Read;
  }
}
