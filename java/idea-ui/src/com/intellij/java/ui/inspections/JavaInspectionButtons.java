// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.impl.EntryPointsManagerImpl;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.ui.CustomComponentExtensionWithSwingRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;

public final class JavaInspectionButtons extends CustomComponentExtensionWithSwingRenderer<JavaConfigurationDialogKind> {
  public JavaInspectionButtons() {
    super("java.option.buttons");
  }

  @Override
  public @NotNull JComponent render(JavaConfigurationDialogKind data, @NotNull OptionController controller, @NotNull Project project) {
    if (project.isDefault()) {
      // Do not provide button for default project
      return new JPanel();
    }
    JButton button = new JButton(getButtonLabel(data));
    button.addActionListener(_ -> showDialog(button, data, project));
    return button;
  }
  
  private static @Nls String getButtonLabel(@NotNull JavaConfigurationDialogKind data) {
    return switch (data) {
      case NULLABILITY_ANNOTATIONS, NULLABILITY_ANNOTATIONS_INSTRUMENTATION -> JavaBundle.message("configure.annotations.option");
      case IMPLICIT_WRITE_ANNOTATIONS, ENTRY_POINT_ANNOTATIONS -> JavaBundle.message("button.annotations");
      case ENTRY_POINT_CODE_PATTERNS -> JavaBundle.message("button.code.patterns");
      case DEPENDENCY_CONFIGURATION -> CodeInsightBundle.message("jvm.inspections.dependency.configure.button.text");
    };
  } 
  
  public static void showDialog(@Nullable Component parent, @NotNull JavaConfigurationDialogKind data, @NotNull Project project) {
    switch (data) {
      case NULLABILITY_ANNOTATIONS -> NullableNotNullDialog.showDialog(project, false);
      case NULLABILITY_ANNOTATIONS_INSTRUMENTATION -> NullableNotNullDialog.showDialog(project, true);
      case ENTRY_POINT_CODE_PATTERNS -> EntryPointsManagerImpl.showEntryPointsDialog(project);
      case IMPLICIT_WRITE_ANNOTATIONS -> EntryPointsManagerBase.getInstance(project).configureAnnotations(true);
      case ENTRY_POINT_ANNOTATIONS -> EntryPointsManagerBase.getInstance(project).configureAnnotations(false);
      case DEPENDENCY_CONFIGURATION -> ShowSettingsUtil.getInstance().editConfigurable(parent, new DependencyConfigurable(project));
    }
  }

  @Override
  public @NotNull String serializeData(JavaConfigurationDialogKind kind) {
    return kind.name();
  }

  @Override
  public JavaConfigurationDialogKind deserializeData(@NotNull String data) {
    return JavaConfigurationDialogKind.valueOf(data);
  }
}
