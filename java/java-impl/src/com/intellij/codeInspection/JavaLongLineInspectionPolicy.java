// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.longLine.LongLineInspectionPolicy;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaLongLineInspectionPolicy implements LongLineInspectionPolicy {
  @Override
  public boolean ignoreLongLineFor(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, false, PsiImportStatementBase.class, PsiPackageStatement.class) != null;
  }
}
