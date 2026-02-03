
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class RefsToSuperViewDescriptor implements UsageViewDescriptor{
  private final PsiClass myClass;
  private final PsiClass mySuper;

  RefsToSuperViewDescriptor(
    PsiClass aClass,
    PsiClass anInterface
  ) {
    myClass = aClass;
    mySuper = anInterface;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myClass, mySuper};
  }

  @Override
  public String getProcessedElementsHeader() {
    return null;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("references.to.0.to.be.replaced.with.references.to.1",
                                            myClass.getName(), mySuper.getName(), UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
