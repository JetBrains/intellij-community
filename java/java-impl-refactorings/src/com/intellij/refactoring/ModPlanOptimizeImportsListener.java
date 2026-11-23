// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanRefactoringListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * Deletes the redundant imports of a Java file that a headless refactoring changed, as {@link OptimizeImportsRefactoringHelper} does
 * after a legacy one: all of them, also the ones that were redundant before, except the ones that a {@link #keepImportEdit} keeps.
 */
@ApiStatus.Internal
public final class ModPlanOptimizeImportsListener implements ModPlanRefactoringListener {
  private static final Key<Boolean> KEPT_IMPORT = Key.create("modplan.kept.import");

  @Override
  public void onRefactoringDone(@NotNull PsiFile copy) {
    if (!(copy instanceof PsiJavaFile javaFile)) return;
    Collection<PsiImportStatementBase> redundantImports = JavaCodeStyleManager.getInstance(copy.getProject()).findRedundantImports(javaFile);
    if (redundantImports == null) return;
    Collection<String> unresolvedImports = new HashSet<>();
    for (PsiImportStatementBase importStatement : redundantImports) {
      if (importStatement.isValid() && importStatement.getUserData(KEPT_IMPORT) == null) {
        OptimizeImportsTask.deleteRedundantImport(importStatement, unresolvedImports);
      }
    }
  }

  /**
   * @return the edit that keeps {@code importStatement}, which the copy takes for redundant, as the reference that needs it resolves to
   * nothing there, such as a reference to a class that the refactoring renames in another file: the copy resolves into the other files as
   * they were before the edits
   */
  public static @NotNull ModPlanEdit keepImportEdit(@NotNull PsiImportStatementBase importStatement) {
    return ModPlanEdit.update(importStatement, copy -> copy.putUserData(KEPT_IMPORT, Boolean.TRUE));
  }
}
