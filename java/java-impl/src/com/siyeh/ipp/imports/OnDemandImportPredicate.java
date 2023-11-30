// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.imports;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

class OnDemandImportPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof PsiImportStatementBase importStatement)) {
      return false;
    }
    if (!importStatement.isOnDemand() || ErrorUtil.containsError(element)) {
      return false;
    }
    if (importStatement instanceof PsiImportStaticStatement && ((PsiImportStaticStatement)importStatement).resolveTargetClass() == null) {
      return false;
    }
    return importStatement.getContainingFile() instanceof PsiJavaFile;
  }
}