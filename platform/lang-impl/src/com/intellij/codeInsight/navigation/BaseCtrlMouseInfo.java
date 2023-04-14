// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.navigation.CtrlMouseDataKt.getReferenceRanges;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
@ApiStatus.Internal
public abstract class BaseCtrlMouseInfo implements CtrlMouseInfo {

  private final @NotNull List<@NotNull TextRange> myRanges;

  protected BaseCtrlMouseInfo(@NotNull List<@NotNull TextRange> ranges) {
    myRanges = ranges;
  }

  protected BaseCtrlMouseInfo(@NotNull PsiElement elementAtPointer) {
    this(getReferenceRanges(elementAtPointer));
  }

  @Override
  public final @NotNull List<@NotNull TextRange> getRanges() {
    return myRanges;
  }
}
