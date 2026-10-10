// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.modplan.ModPlanRefactoringListener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * Deletes the redundant imports of a Java file that a headless refactoring changed, as {@link OptimizeImportsRefactoringHelper} does
 * after a legacy one: all of them, also the ones that were redundant before.
 */
final class ModPlanOptimizeImportsListener implements ModPlanRefactoringListener {
  @Override
  public void onRefactoringDone(@NotNull PsiFile copy) {
    if (!(copy instanceof PsiJavaFile javaFile)) return;
    Collection<PsiImportStatementBase> redundantImports = JavaCodeStyleManager.getInstance(copy.getProject()).findRedundantImports(javaFile);
    if (redundantImports == null) return;
    Collection<String> unresolvedImports = new HashSet<>();
    for (PsiImportStatementBase importStatement : redundantImports) {
      if (importStatement.isValid()) {
        OptimizeImportsTask.deleteRedundantImport(importStatement, unresolvedImports);
      }
    }
  }
}
