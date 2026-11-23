// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.options.JavaConfigurationButtonProvider;
import com.intellij.codeInsight.options.JavaConfigurationDialogInfo;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

final class JavaConfigurationDialogButtonProvider extends JavaConfigurationButtonProvider {
  @Override
  public @NotNull OptCustom button(@NotNull JavaConfigurationDialogInfo info) {
    return new JavaConfigurationDialogs().component(info);
  }

  @Override
  public boolean isConfigurationUISupported(@NotNull JavaConfigurationDialogInfo kind) {
    return true;
  }

  @Override
  public void showConfigurationDialog(@NotNull JavaConfigurationDialogInfo info, @Nullable Component parent, @NotNull Project project) {
    JavaConfigurationDialogs.showDialog(parent, info, project);
  }
}
