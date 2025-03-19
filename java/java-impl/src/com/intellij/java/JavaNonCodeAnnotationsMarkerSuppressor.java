// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.codeInsight.NonCodeAnnotationsMarkerSuppressor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastUtils;

final class JavaNonCodeAnnotationsMarkerSuppressor implements NonCodeAnnotationsMarkerSuppressor {
  @Override
  public boolean isLineMarkerSuppressed(@NotNull PsiElement element) {
    UElement uElement = UastUtils.getUParentForIdentifier(element);

    if (uElement instanceof UMethod uMethod
        && "toString".equals(uMethod.getName())
        && uMethod.getUastParameters().isEmpty()) {
      return true; // toString supposed to have the standard contract, and there is no real need to add explicit annotations there
    }

    return false;
  }
}
