// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BrokenUsage extends UsageInfo2UsageAdapter {

  private final SmartPsiElementPointer<PsiElement> myElementPointer;

  BrokenUsage(@NotNull UsageInfo usageInfo, PsiElement reportedElement) {
    super(usageInfo);
    myElementPointer = SmartPointerManager.createPointer(reportedElement);
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myElementPointer.getElement() != null;
  }

  @Override
  public Segment getNavigationRange() {
    PsiElement reportedElement = myElementPointer.getElement();
    return reportedElement == null ? null : reportedElement.getTextRange();
  }

  public @Nullable PsiElement getReportedElement() {
    return myElementPointer.getElement();
  }
}
