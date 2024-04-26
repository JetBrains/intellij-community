// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonCodeUsageInfoFactory implements UsageInfoFactory {
  private final PsiElement myElement;
  private final String myStringToReplace;

  public NonCodeUsageInfoFactory(final PsiElement element, final String stringToReplace) {
    myElement = element;
    myStringToReplace = stringToReplace;
  }

  @Override
  public @Nullable UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
    final PsiElement namedElement = TargetElementUtilBase.getNamedElement(usage, startOffset);
    if (namedElement != null) {
      return null;
    }

    int start = usage.getTextRange().getStartOffset();
    return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, myElement, myStringToReplace);
  }
}