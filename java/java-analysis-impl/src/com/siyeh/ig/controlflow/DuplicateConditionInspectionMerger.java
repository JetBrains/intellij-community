// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public final class DuplicateConditionInspectionMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "DuplicateCondition";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "DuplicateCondition",
      "DuplicateBooleanBranch"
    };
  }
}
