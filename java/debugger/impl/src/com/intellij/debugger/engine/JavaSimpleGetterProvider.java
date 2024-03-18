// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nikolay.Tropin
 */
public final class JavaSimpleGetterProvider implements SimplePropertyGetterProvider {
  @Override
  public boolean isInsideSimpleGetter(@NotNull PsiElement element) {
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) return false;

    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }

    final PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return false;
    }

    final PsiStatement statement = statements[0];
    if (!(statement instanceof PsiReturnStatement)) {
      return false;
    }

    final PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
    if (!(value instanceof PsiReferenceExpression reference)) {
      return false;
    }

    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier != null && !"this".equals(qualifier.getText())) {
      return false;
    }

    final PsiElement referent = reference.resolve();
    if (referent == null) {
      return false;
    }

    if (!(referent instanceof PsiField)) {
      return false;
    }

    return Comparing.equal(((PsiField)referent).getContainingClass(), method.getContainingClass());
  }
}
