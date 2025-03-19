// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class ReferencedElementsCollector extends JavaRecursiveElementVisitor {
  final HashSet<PsiMember> myReferencedMembers = new HashSet<>();

  @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiMember) {
      checkAddMember((PsiMember)psiElement);
    }
    super.visitReferenceElement(reference);
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    PsiMethod method = expression.resolveMethod();
    if (method != null) {
      checkAddMember(method);
    }
    super.visitNewExpression(expression);
  }

  protected void checkAddMember(final @NotNull PsiMember member) {
    myReferencedMembers.add(member);
  }
}
