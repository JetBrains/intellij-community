// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class RemoveMiddlemanUsageViewDescriptor implements UsageViewDescriptor {
  private @NotNull final PsiField field;

  RemoveMiddlemanUsageViewDescriptor(@NotNull PsiField field) {
    super();
    this.field = field;
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactorJBundle
      .message("references.to.expose.usage.view", usagesCount, filesCount);
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactorJBundle.message("remove.middleman.field.header");
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{field};
  }
}
