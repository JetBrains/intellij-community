// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
@ApiStatus.Internal
public class MultipleTargetElementsInfo extends BaseCtrlMouseInfo {

  public MultipleTargetElementsInfo(@NotNull List<@NotNull TextRange> ranges) {
    super(ranges);
  }

  public MultipleTargetElementsInfo(@NotNull PsiElement elementAtPointer) {
    super(elementAtPointer);
  }

  @Override
  public @NotNull CtrlMouseDocInfo getDocInfo() {
    return new CtrlMouseDocInfo(CodeInsightBundle.message("multiple.implementations.tooltip"), null, null);
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
