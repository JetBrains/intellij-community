// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ConstantValueInspectionMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "ConstantValue";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[] {"ConstantConditions"};
  }

  @Override
  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    ConstantValueInspection inspection = new ConstantValueInspection();
    inspection.DONT_REPORT_TRUE_ASSERT_STATEMENTS =
      Boolean.parseBoolean(JDOMExternalizerUtil.readField(sourceElement, "DONT_REPORT_TRUE_ASSERT_STATEMENTS", "false"));
    inspection.IGNORE_ASSERT_STATEMENTS =
      Boolean.parseBoolean(JDOMExternalizerUtil.readField(sourceElement, "IGNORE_ASSERT_STATEMENTS", "false"));
    inspection.REPORT_CONSTANT_REFERENCE_VALUES =
      Boolean.parseBoolean(JDOMExternalizerUtil.readField(sourceElement, "REPORT_CONSTANT_REFERENCE_VALUES", "true"));
    inspection.writeSettings(toolElement);
    return toolElement;
  }
}
