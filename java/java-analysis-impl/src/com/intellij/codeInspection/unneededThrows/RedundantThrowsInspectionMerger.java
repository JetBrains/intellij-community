// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public final class RedundantThrowsInspectionMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "RedundantThrows";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {"RedundantThrowsDeclaration"};
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return getSourceToolNames();
  }
}
