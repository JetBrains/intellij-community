// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.move.moveInner;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class MoveInnerViewDescriptor implements UsageViewDescriptor {

  private final PsiClass myInnerClass;

  MoveInnerViewDescriptor(PsiClass innerClass) {
    myInnerClass = innerClass;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myInnerClass};
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaRefactoringBundle.message("move.inner.class.to.be.moved");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
