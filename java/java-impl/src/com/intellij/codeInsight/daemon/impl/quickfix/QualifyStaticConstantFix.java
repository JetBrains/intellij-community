// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

public class QualifyStaticConstantFix extends StaticImportConstantFix {
  public QualifyStaticConstantFix(@NotNull PsiFile psiFile, @NotNull PsiJavaCodeReferenceElement referenceElement) {
    super(psiFile, referenceElement);
  }

  @Override
  protected @NotNull String getBaseText() {
    return JavaBundle.message("qualify.static.constant.access");
  }

  @Override
  protected void performImport(@NotNull PsiField toImport, @NotNull PsiJavaCodeReferenceElement refElement) {
    if (refElement instanceof PsiReferenceExpression ref) {
      QualifyStaticMethodCallFix.qualifyStatically(toImport, toImport.getProject(), ref);
    }
  }

  @Override
  boolean toAddStaticImports() {
    return false;
  }
}
