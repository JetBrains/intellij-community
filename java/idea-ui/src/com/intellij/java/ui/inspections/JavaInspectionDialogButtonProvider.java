// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInsight.options.JavaInspectionButtonProvider;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

final class JavaInspectionDialogButtonProvider extends JavaInspectionButtonProvider {
  @Override
  public @NotNull OptCustom button(@NotNull JavaConfigurationDialogKind kind) {
    return new JavaInspectionButtons().component(kind);
  }

  @Override
  public boolean isConfigurationUISupported(@NotNull JavaConfigurationDialogKind kind) {
    return true;
  }

  @Override
  public void showConfigurationDialog(@NotNull JavaConfigurationDialogKind kind, @Nullable Component parent, @NotNull Project project) {
    JavaInspectionButtons.showDialog(parent, kind, project);
  }
}
