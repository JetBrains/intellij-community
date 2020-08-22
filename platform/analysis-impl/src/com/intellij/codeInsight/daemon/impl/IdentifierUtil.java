// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IdentifierUtil {
  @Nullable
  public static PsiElement getNameIdentifier(@NotNull PsiElement element) {
    if (element instanceof PsiNameIdentifierOwner) {
      return ((PsiNameIdentifierOwner)element).getNameIdentifier();
    }

    if (element.isPhysical() &&
        element instanceof PsiNamedElement &&
        element.getContainingFile() != null &&
        element.getTextRange() != null) {
      // Quite hacky way to get name identifier. Depends on getTextOffset overriden properly.
      PsiElement potentialIdentifier = element.findElementAt(element.getTextOffset() - element.getTextRange().getStartOffset());
      if (potentialIdentifier != null && Comparing.equal(potentialIdentifier.getText(), ((PsiNamedElement)element).getName(), false)) {
        return potentialIdentifier;
      }
    }

    return null;
  }
}
