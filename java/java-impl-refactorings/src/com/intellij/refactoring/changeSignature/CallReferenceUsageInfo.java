// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;


public class CallReferenceUsageInfo extends UsageInfo {
  private final PsiCallReference myReference;

  public CallReferenceUsageInfo(@NotNull PsiCallReference reference) {
    super(reference);
    myReference = reference;
  }

  @Override
  public PsiCallReference getReference() {
    return myReference;
  }
}
