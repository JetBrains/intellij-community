// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class RunDialog {

  public static boolean editConfiguration(final Project project, @NotNull RunnerAndConfigurationSettings configuration, @NlsContexts.DialogTitle String title) {
    return editConfiguration(project, configuration, title, null);
  }

  public static boolean editConfiguration(@NotNull ExecutionEnvironment environment, @NotNull @NlsContexts.DialogTitle String title) {
    //noinspection ConstantConditions
    return editConfiguration(environment.getProject(), environment.getRunnerAndConfigurationSettings(), title, environment.getExecutor());
  }

  public static boolean editConfiguration(final Project project,
                                          @NotNull RunnerAndConfigurationSettings configuration,
                                          @NlsContexts.DialogTitle String title,
                                          final @Nullable Executor executor) {
    SingleConfigurationConfigurable<RunConfiguration> configurable = SingleConfigurationConfigurable.editSettings(configuration, executor);
    final SingleConfigurableEditor dialog = new SingleConfigurableEditor(project, configurable, null, DialogWrapper.IdeModalityType.IDE) {
      {
        if (executor != null) {
          setOKButtonText(executor.getActionName());
          //setOKButtonIcon(executor.getIcon());
        }
      }

      @Override
      public Dimension getInitialSize() {
        return new Dimension(650, 500);
      }
    };

    dialog.setTitle(title);
    return dialog.showAndGet();
  }
}