
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class EncapsulateFieldsViewDescriptor implements UsageViewDescriptor {
  private final PsiField[] myFields;

  EncapsulateFieldsViewDescriptor(FieldDescriptor[] descriptors) {
    myFields = new PsiField[descriptors.length];
    for (int i = 0; i < descriptors.length; i++) {
      myFields[i] = descriptors[i].getField();
    }
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaRefactoringBundle.message("encapsulate.fields.fields.to.be.encapsulated");
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myFields;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
