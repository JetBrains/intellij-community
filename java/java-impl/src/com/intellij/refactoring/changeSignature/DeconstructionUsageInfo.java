// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DeconstructionUsageInfo extends UsageInfo {
  private final @NotNull PsiDeconstructionPattern myDeconstruction;

  public DeconstructionUsageInfo(final @NotNull PsiDeconstructionPattern deconstruction) {
    super(deconstruction);
    myDeconstruction = deconstruction;
  }

  @NotNull
  public PsiDeconstructionPattern getDeconstruction() {
    return myDeconstruction;
  }
}
