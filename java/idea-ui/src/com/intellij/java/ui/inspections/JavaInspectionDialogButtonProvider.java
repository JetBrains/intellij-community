// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.options.JavaControlButtonKind;
import com.intellij.codeInsight.options.JavaInspectionButtonProvider;
import com.intellij.codeInspection.options.OptCustom;
import org.jetbrains.annotations.NotNull;

final class JavaInspectionDialogButtonProvider extends JavaInspectionButtonProvider {
  @Override
  public @NotNull OptCustom button(@NotNull JavaControlButtonKind kind) {
    return new JavaInspectionButtons().component(kind);
  }
}
