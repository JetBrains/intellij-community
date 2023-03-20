// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class TypeMigrationViewDescriptor implements UsageViewDescriptor {

  private final PsiElement myElement;

  TypeMigrationViewDescriptor(PsiElement elements) {
    myElement = elements;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{myElement};
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaBundle.message("type.migration.processed.elements.header");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("occurrences.to.be.migrated", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
