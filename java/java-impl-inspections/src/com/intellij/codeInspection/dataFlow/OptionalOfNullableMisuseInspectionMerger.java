// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class OptionalOfNullableMisuseInspectionMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "OptionalOfNullableMisuse";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[] {"DataFlowIssue", "ConstantConditions"};
  }
}
