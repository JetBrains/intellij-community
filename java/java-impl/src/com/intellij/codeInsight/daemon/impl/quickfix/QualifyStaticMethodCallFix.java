// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class QualifyStaticMethodCallFix extends StaticImportMethodFix {
  public QualifyStaticMethodCallFix(@NotNull PsiFile psiFile, @NotNull PsiMethodCallExpression methodCallExpression) {
    super(psiFile, methodCallExpression);
  }

  @Override
  protected @NotNull String getBaseText() {
    return JavaBundle.message("qualify.static.call.fix.text");
  }

  @Override
  protected void performImport(@NotNull PsiMethod toImport, @NotNull PsiMethodCallExpression ref) {
    qualifyStatically(toImport, toImport.getProject(), ref.getMethodExpression());
  }

  @Override
  boolean toAddStaticImports() {
    return false;
  }

  static void qualifyStatically(@NotNull PsiMember toImport,
                                @NotNull Project project,
                                @NotNull PsiReferenceExpression qualifiedExpression) {
    PsiClass containingClass = toImport.getContainingClass();
    if (containingClass == null) return;
    PsiReferenceExpression qualifier = JavaPsiFacade.getElementFactory(project).createReferenceExpression(containingClass);
    qualifiedExpression.setQualifierExpression(qualifier);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifiedExpression);
  }
}
