
package com.intellij.refactoring.encapsulateFields;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class EncapsulateFieldsViewDescriptor implements UsageViewDescriptor {
  private final PsiField[] myFields;

  public EncapsulateFieldsViewDescriptor(PsiField[] fields) {
    myFields = fields;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("encapsulate.fields.fields.to.be.encapsulated");
  }

  @NotNull
  public PsiElement[] getElements() {
    return myFields;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
