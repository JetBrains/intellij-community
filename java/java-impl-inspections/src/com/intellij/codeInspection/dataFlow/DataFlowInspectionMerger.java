// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class DataFlowInspectionMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "DataFlowIssue";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[] {"ConstantConditions"};
  }
}
