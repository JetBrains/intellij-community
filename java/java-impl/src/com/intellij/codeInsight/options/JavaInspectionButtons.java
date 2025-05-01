// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInspection.ex.impl.EntryPointsManagerImpl;
import com.intellij.codeInspection.ui.CustomComponentExtensionWithSwingRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaInspectionButtons extends CustomComponentExtensionWithSwingRenderer<JavaInspectionButtons.ButtonKind> {
  public JavaInspectionButtons() {
    super("java.option.buttons");
  }

  @Override
  public @NotNull JComponent render(ButtonKind data, @NotNull Project project) {
    if (project.isDefault()) {
      // Do not provide button for default project
      return new JPanel();
    }
    return switch (data) {
      case NULLABILITY_ANNOTATIONS -> NullableNotNullDialog.createConfigureAnnotationsButton(project);
      case ENTRY_POINT_CODE_PATTERNS -> EntryPointsManagerImpl.createConfigureClassPatternsButton(project);
      case ENTRY_POINT_ANNOTATIONS -> EntryPointsManagerImpl.createConfigureAnnotationsButton(project, false);
      case IMPLICIT_WRITE_ANNOTATIONS -> EntryPointsManagerImpl.createConfigureAnnotationsButton(project, true);
      case DEPENDENCY_CONFIGURATION -> DependencyConfigurable.getConfigureButton(project);
    };
  }

  @Override
  public @NotNull String serializeData(ButtonKind kind) {
    return kind.name();
  }

  @Override
  public ButtonKind deserializeData(@NotNull String data) {
    return ButtonKind.valueOf(data);
  }

  public enum ButtonKind {
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
    DEPENDENCY_CONFIGURATION
  }
}
