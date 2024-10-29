// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jetbrains.annotations.NotNull;

public final class RedundantJavaTimeOperationMerger extends InspectionElementsMergerBase {

  @Override
  public @NotNull String getMergedToolName() {
    return "RedundantJavaTimeOperations";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "RedundantCreationJavaTime", "RedundantCompareToJavaTime", "RedundantExplicitChronoField"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "RedundantCreationJavaTime", "RedundantCompareToJavaTime", "RedundantExplicitChronoField"
    };
  }
}
