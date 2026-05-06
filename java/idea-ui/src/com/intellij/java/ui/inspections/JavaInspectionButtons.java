// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInsight.options.JavaControlButtonKind;
import com.intellij.codeInspection.ex.impl.EntryPointsManagerImpl;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.ui.CustomComponentExtensionWithSwingRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;

public final class JavaInspectionButtons extends CustomComponentExtensionWithSwingRenderer<JavaControlButtonKind> {
  public JavaInspectionButtons() {
    super("java.option.buttons");
  }

  @Override
  public @NotNull JComponent render(JavaControlButtonKind data, @NotNull OptionController controller, @NotNull Project project) {
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
  public @NotNull String serializeData(JavaControlButtonKind kind) {
    return kind.name();
  }

  @Override
  public JavaControlButtonKind deserializeData(@NotNull String data) {
    return JavaControlButtonKind.valueOf(data);
  }
}
