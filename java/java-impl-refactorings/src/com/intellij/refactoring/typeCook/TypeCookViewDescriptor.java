// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeCook;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class TypeCookViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElements;

  TypeCookViewDescriptor(PsiElement[] elements) {
    myElements = elements;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaRefactoringBundle.message("type.cook.elements.header");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("declaration.s.to.be.generified", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
