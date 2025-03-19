// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public final class MethodOverridesInaccessibleMethodMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "MethodOverridesInaccessibleMethodOfSuper";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] { "MethodOverridesPackageLocalMethod", "MethodOverridesPrivateMethod"};
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {"MethodOverridesPrivateMethodOfSuperclass", "MethodOverridesPrivateMethodOfSuperclass" };
  }
}
