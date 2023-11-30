// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryModifierInspectionMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "UnnecessaryModifier";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[] {
      "UnnecessaryEnumModifier",
      "UnnecessaryInterfaceModifier",
      "UnnecessaryRecordModifier"
    };
  }
}
