// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unusedImport;

import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;

import java.util.Comparator;

/**
 * @author Bas Leijdekkers
 */
final class ImportStatementComparator implements Comparator<PsiImportStatementBase> {
  public static final ImportStatementComparator INSTANCE = new ImportStatementComparator();

  private ImportStatementComparator() {}

  public static ImportStatementComparator getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(PsiImportStatementBase importStatementBase1, PsiImportStatementBase importStatementBase2) {
    final boolean onDemand1 = importStatementBase1.isOnDemand();
    final boolean onDemand2 = importStatementBase2.isOnDemand();
    if (onDemand1 != onDemand2) {
      return onDemand1 ? -1 : 1;
    }
    if (onDemand1) {
      boolean isModule1 = importStatementBase1 instanceof PsiImportModuleStatement;
      boolean isModule2 = importStatementBase2 instanceof PsiImportModuleStatement;
      if (isModule1 != isModule2) {
        return isModule1 ? -1 : 1;
      }
    }
    // just sort on module import first, then on demand imports, and sort the rest in reverse file order.
    return -PsiElementOrderComparator.getInstance().compare(importStatementBase1, importStatementBase2);
  }
}
