package com.intellij.refactoring.typeMigration;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class TypeMigrationViewDescriptor implements UsageViewDescriptor {

  private final PsiElement myElement;

  public TypeMigrationViewDescriptor(PsiElement elements) {
    myElement = elements;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{myElement};
  }

  public String getProcessedElementsHeader() {
    return "Root for type migration";
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("occurences.to.be.migrated", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
