/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.unusedImport;

import com.intellij.psi.PsiImportStatementBase;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;

import java.util.Comparator;

/**
 * @author Bas Leijdekkers
 */
class ImportStatementComparator implements Comparator<PsiImportStatementBase> {
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
