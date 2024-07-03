// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unusedImport;

import com.intellij.psi.PsiImportDeclaration;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;

import java.util.Comparator;

/**
 * @author Bas Leijdekkers
 */
final class ImportStatementComparator implements Comparator<PsiImportDeclaration> {
  public static final ImportStatementComparator INSTANCE = new ImportStatementComparator();

  private ImportStatementComparator() {}

  public static ImportStatementComparator getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(PsiImportDeclaration importStatementDeclaration1, PsiImportDeclaration importStatementDeclaration2) {
    int type1 = getCompareType(importStatementDeclaration1);
    int type2 = getCompareType(importStatementDeclaration2);
    if (type1 != type2) {
      return type1 > type2 ? -1 : 1;
    }
    // just sort on demand imports first, and sort the rest in reverse file order.
    return -PsiElementOrderComparator.getInstance().compare(importStatementDeclaration1, importStatementDeclaration2);
  }

  /**
   * Returns the comparison type for a given import statement.
   *
   * @param importStatement the import statement to analyze
   * @return an integer representing the type of import:
   *         0 it's an import,
   *         1 if it's an on-demand import,
   *         2 if it's a module import,
   *         4 otherwise
   */
  private static int getCompareType(PsiImportDeclaration importStatement) {
    if (importStatement instanceof PsiImportStatementBase importStatementBase) {
      return importStatementBase.isOnDemand() ? 1 : 0;
    }
    else if (importStatement instanceof PsiImportModuleStatement) {
      return 2;
    } else {
      return 4;
    }
  }
}
