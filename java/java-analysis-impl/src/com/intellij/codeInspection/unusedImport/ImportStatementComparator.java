// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unusedImport;

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
    final boolean onDemand = importStatementBase1.isOnDemand();
    if (onDemand != importStatementBase2.isOnDemand()) {
      return onDemand ? -1 : 1;
    }
    // just sort on demand imports first, and sort the rest in reverse file order.
    return -PsiElementOrderComparator.getInstance().compare(importStatementBase1, importStatementBase2);
  }
}
