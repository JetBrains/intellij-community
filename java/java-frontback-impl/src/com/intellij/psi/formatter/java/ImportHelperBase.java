// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import org.jetbrains.annotations.NotNull;

public class ImportHelperBase {
  protected final JavaCodeStyleSettings mySettings;

  public ImportHelperBase(@NotNull JavaCodeStyleSettings settings) {
    mySettings = settings;
  }

  public int getEmptyLinesBetween(@NotNull PsiImportStatementBase statement1, @NotNull PsiImportStatementBase statement2) {
    int index1 = findEntryIndex(statement1);
    int index2 = findEntryIndex(statement2);
    if (index1 == index2) return 0;
    if (index1 > index2) {
      int t = index1;
      index1 = index2;
      index2 = t;
    }
    PackageEntry[] entries = mySettings.IMPORT_LAYOUT_TABLE.getEntries();
    int maxSpace = 0;
    for (int i = index1 + 1; i < index2; i++) {
      if (entries[i] == PackageEntry.BLANK_LINE_ENTRY) {
        int space = 0;
        //noinspection AssignmentToForLoopParameter
        do {
          space++;
        }
        while (entries[++i] == PackageEntry.BLANK_LINE_ENTRY);
        maxSpace = Math.max(maxSpace, space);
      }
    }
    return maxSpace;
  }

  protected static int findEntryIndex(@NotNull String packageName, boolean isStatic, boolean isModule, PackageEntry @NotNull [] entries) {
    PackageEntry bestEntry = null;
    int bestEntryIndex = -1;
    int allOtherStaticIndex = -1;
    int allOtherIndex = -1;
    for (int i = 0; i < entries.length; i++) {
      PackageEntry entry = entries[i];
      if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
        allOtherStaticIndex = i;
      }
      if (!isModule && entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
        allOtherIndex = i;
      }
      if (!isModule && entry.isBetterMatchForPackageThan(bestEntry, packageName, isStatic)) {
        bestEntry = entry;
        bestEntryIndex = i;
      }
      if (isModule && entry == PackageEntry.ALL_MODULE_IMPORTS) {
        bestEntry = entry;
        bestEntryIndex = i;
      }
    }
    if (bestEntryIndex == -1 && isStatic && allOtherStaticIndex == -1 && allOtherIndex != -1) {
      // if no layout for static imports specified, put them among all others
      bestEntryIndex = allOtherIndex;
    }
    return bestEntryIndex;
  }

  public int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (statement instanceof PsiImportModuleStatement) {
      return findEntryIndex("",
                            mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY && statement instanceof PsiImportStaticStatement,
                            true,
                            mySettings.IMPORT_LAYOUT_TABLE.getEntries());
    }
    if (ref == null) return -1;
    String packageName = statement.isOnDemand() ? ref.getCanonicalText() : StringUtil.getPackageName(ref.getCanonicalText());
    return findEntryIndex(packageName,
                          mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY && statement instanceof PsiImportStaticStatement,
                          false,
                          mySettings.IMPORT_LAYOUT_TABLE.getEntries());
  }
}
