// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.invertBoolean;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

public final class InvertBooleanUsageViewDescriptor implements UsageViewDescriptor {
  private final PsiElement myElement;

  public InvertBooleanUsageViewDescriptor(final PsiElement element) {
    myElement = element;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myElement};
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("invert.boolean.elements.header", UsageViewUtil.getType(myElement));
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("invert.boolean.refs.to.invert", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
