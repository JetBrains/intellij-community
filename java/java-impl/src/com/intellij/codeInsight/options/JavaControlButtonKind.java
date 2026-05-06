// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptRegularComponent;

public enum JavaControlButtonKind {
  NULLABILITY_ANNOTATIONS,
  ENTRY_POINT_CODE_PATTERNS,
  /**
   * Entry point annotations + implicit write annotations
   */
  ENTRY_POINT_ANNOTATIONS,
  /**
   * Implicit write annotations only
   */
  IMPLICIT_WRITE_ANNOTATIONS,
  DEPENDENCY_CONFIGURATION;

  public OptRegularComponent button() {
    return JavaInspectionButtonProvider.getInstance().button(this);
  }
}
