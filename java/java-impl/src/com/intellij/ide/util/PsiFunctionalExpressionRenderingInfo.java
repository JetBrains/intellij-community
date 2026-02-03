// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public final class PsiFunctionalExpressionRenderingInfo implements PsiElementRenderingInfo<PsiFunctionalExpression> {

  public static final PsiElementRenderingInfo<PsiFunctionalExpression> INSTANCE = new PsiFunctionalExpressionRenderingInfo();

  private PsiFunctionalExpressionRenderingInfo() { }

  @Override
  public @NotNull String getPresentableText(@NotNull PsiFunctionalExpression element) {
    return ClassPresentationUtil.getFunctionalExpressionPresentation(element, false);
  }

  @Override
  public @Nullable String getContainerText(@NotNull PsiFunctionalExpression element) {
    return PsiClassRenderingInfo.getContainerTextStatic(element);
  }
}
