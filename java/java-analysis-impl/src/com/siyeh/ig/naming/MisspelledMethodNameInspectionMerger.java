// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public final class MisspelledMethodNameInspectionMerger extends InspectionElementsMerger {

  @Override
  public @NotNull String getMergedToolName() {
    return "MisspelledMethodName";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[]{"MethodNamesDifferOnlyByCase", "MisspelledSetUp", "MisspelledTearDown", "MisspelledHashcode", "MisspelledToString", "MisspelledCompareTo"};
  }
}
