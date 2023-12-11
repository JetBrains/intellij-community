// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jetbrains.annotations.NotNull;

public final class ForwardCompatibilityInspectionMerger extends InspectionElementsMergerBase {

  @NotNull
  @Override
  public String getMergedToolName() {
    return "ForwardCompatibility";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "AssertAsName", "EnumAsName"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "AssertAsIdentifier", "EnumAsIdentifier"
    };
  }
}
