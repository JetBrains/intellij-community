// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class MigrationUsagesViewDescriptor implements UsageViewDescriptor {
  private final boolean isSearchInComments;
  private final MigrationMap myMigrationMap;

  MigrationUsagesViewDescriptor(MigrationMap migrationMap, boolean isSearchInComments) {
    myMigrationMap = migrationMap;
    this.isSearchInComments = isSearchInComments;
  }

  public MigrationMap getMigrationMap() {
    return myMigrationMap;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public String getProcessedElementsHeader() {
    return null;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("references.in.code.to.elements.from.migration.map", myMigrationMap.getName(),
                                     UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getInfo() {
    return JavaRefactoringBundle.message("press.the.do.migrate.button", myMigrationMap.getName());
  }

}
