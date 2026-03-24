// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jetbrains.annotations.NotNull;

public final class CatchMayIgnoreExceptionMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "CatchMayIgnoreException";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {"EmptyCatchBlock", "UnusedCatchParameter"};
  }
}
