// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jetbrains.annotations.NotNull;

public final class CatchMayIgnoreExceptionMerger extends InspectionElementsMergerBase {
  @NotNull
  @Override
  public String getMergedToolName() {
    return "CatchMayIgnoreException";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {"EmptyCatchBlock", "UnusedCatchParameter"};
  }
}
