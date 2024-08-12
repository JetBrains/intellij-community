// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public final class IntroduceParameterObjectUsageViewDescriptor implements UsageViewDescriptor {

  private final PsiElement method;

  public IntroduceParameterObjectUsageViewDescriptor(PsiElement method) {
    this.method = method;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{method};
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("refactoring.introduce.parameter.object.method.whose.parameters.are.to.wrapped");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("refactoring.introduce.parameter.object.references.to.be.modified") +
           UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }
}
