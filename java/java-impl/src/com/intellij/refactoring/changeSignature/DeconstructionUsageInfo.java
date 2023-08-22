// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

final class DeconstructionUsageInfo extends UsageInfo {
  private final @NotNull PsiDeconstructionPattern myDeconstruction;

  DeconstructionUsageInfo(final @NotNull PsiDeconstructionPattern deconstruction) {
    super(deconstruction);
    myDeconstruction = deconstruction;
  }

  @NotNull
  PsiDeconstructionPattern getDeconstruction() {
    return myDeconstruction;
  }
}
