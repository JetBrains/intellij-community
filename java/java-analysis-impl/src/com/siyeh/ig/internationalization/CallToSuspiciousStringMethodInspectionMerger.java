// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class CallToSuspiciousStringMethodInspectionMerger extends InspectionElementsMerger {

  @Override
  public @NotNull String getMergedToolName() {
    return "CallToSuspiciousStringMethod";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "StringEquals",
      "StringEqualsIgnoreCase",
      "StringCompareTo"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "CallToStringEquals",
      "CallToStringEqualsIgnoreCase",
      "CallToStringCompareTo"
    };
  }
}
