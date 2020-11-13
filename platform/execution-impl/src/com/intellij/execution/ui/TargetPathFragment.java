// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;

public class TargetPathFragment<T extends TargetEnvironmentAwareRunProfile> extends SettingsEditorFragment<T, LabeledComponent<JTextField>> {

  public static final String ID = "target.project.path";

  public TargetPathFragment() {
    super(ID, null, null,
          LabeledComponent.create(new JTextField(), ExecutionBundle.message("label.project.path.on.target"), BorderLayout.WEST), -1,
          (t, component) -> component.getComponent().setText(getPath(t)),
          (t, component) -> setPath(t, component.getComponent().getText()),
          t -> t.getDefaultTargetName() != null);
  }

  private static String getPath(TargetEnvironmentAwareRunProfile t) {
    ModuleBasedConfiguration<?, ?> configuration = (ModuleBasedConfiguration)t;
    if (configuration.getProjectPathOnTarget() != null) {
      return configuration.getProjectPathOnTarget();
    }

    String targetName = t.getDefaultTargetName();
    if (targetName == null) return "";
    TargetEnvironmentConfiguration targetEnvironmentConfiguration =
      TargetEnvironmentsManager.getInstance(configuration.getProject()).getTargets().findByName(targetName);
    return targetEnvironmentConfiguration == null ? null : targetEnvironmentConfiguration.getProjectRootOnTarget();
  }

  private static void setPath(TargetEnvironmentAwareRunProfile t, String path) {
    ModuleBasedConfiguration<?, ?> configuration = (ModuleBasedConfiguration)t;
    configuration.setProjectPathOnTarget(path);

    String targetName = t.getDefaultTargetName();
    if (targetName == null) return;
    TargetEnvironmentConfiguration targetEnvironmentConfiguration =
      TargetEnvironmentsManager.getInstance(configuration.getProject()).getTargets().findByName(targetName);
    if (targetEnvironmentConfiguration != null && Comparing.strEqual(targetEnvironmentConfiguration.getProjectRootOnTarget(), path)) {
      configuration.setProjectPathOnTarget(null);
    }
  }
}
