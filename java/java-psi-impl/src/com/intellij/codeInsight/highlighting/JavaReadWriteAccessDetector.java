// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;


public class JavaReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(@NotNull final PsiElement element) {
    return element instanceof PsiVariable && !(element instanceof ImplicitVariable)
           || element instanceof PsiClass
           || element instanceof PsiAnnotationMethod && !(element instanceof PsiCompiledElement);
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull final PsiElement element) {
    if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
      return true;
    }
    if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiForeachStatement) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Access getReferenceAccess(@NotNull final PsiElement referencedElement, @NotNull final PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @NotNull
  @Override
  public Access getExpressionAccess(@NotNull final PsiElement expression) {
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
