// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public class ConstantAssertArgumentMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "ConstantAssertArgument";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "ConstantJUnitAssertArgument",
      "ConstantTestNGAssertArgument"
    };
  }
}
