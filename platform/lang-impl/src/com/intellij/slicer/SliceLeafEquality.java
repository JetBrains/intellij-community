// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliceLeafEquality implements HashingStrategy<PsiElement> {
  protected @NotNull PsiElement substituteElement(@NotNull PsiElement element) {
    return element;
  }

  @Override
  public int hashCode(@Nullable PsiElement element) {
    if (element == null) return 0;
    String text = ReadAction.compute(() -> {
      PsiElement elementToCompare = substituteElement(element);
      return elementToCompare instanceof PsiNamedElement ? ((PsiNamedElement)elementToCompare).getName()
                                                         : AstBufferUtil.getTextSkippingWhitespaceComments(elementToCompare.getNode());
    });
    return Comparing.hashcode(text);
  }

  @Override
  public boolean equals(@Nullable PsiElement o1, @Nullable PsiElement o2) {
    if (o1 == o2) {
      return true;
    }
    if (o1 == null || o2 == null) {
      return false;
    }
    return ReadAction.compute(() -> PsiEquivalenceUtil.areElementsEquivalent(o1, o2));
  }
}
