// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RunContextAction extends BaseRunConfigurationAction {
  private final Executor myExecutor;

  public RunContextAction(@NotNull final Executor executor) {
    super(ExecutionBundle.message("perform.action.with.context.configuration.action.name", executor.getStartActionText()), null, new IconLoader.LazyIcon() {
      @Override
      protected Icon compute() {
        return executor.getIcon();
      }
    });
    myExecutor = executor;
  }

  @Override
  protected void perform(final ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = context.findExisting();
    final RunManagerEx runManager = (RunManagerEx)context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) {
        return;
      }
      runManager.setTemporaryConfiguration(configuration);
    }
    if (Registry.is("select.run.configuration.from.context")) {
      runManager.setSelectedConfiguration(configuration);
    }

    ExecutionUtil.runConfiguration(configuration, myExecutor);
  }

  @Override
  protected boolean isEnabledFor(RunConfiguration configuration) {
    return getRunner(configuration) != null;
  }

  @Nullable
  private ProgramRunner getRunner(final RunConfiguration configuration) {
    return RunnerRegistry.getInstance().getRunner(myExecutor.getId(), configuration);
  }

  @Override
  protected void updatePresentation(final Presentation presentation, @NotNull final String actionText, final ConfigurationContext context) {
    presentation.setText(myExecutor.getStartActionText(actionText), true);

    Pair<Boolean, Boolean> b = isEnabledAndVisible(context);

    presentation.setEnabled(b.first);
    presentation.setVisible(b.second);
  }

  private Pair<Boolean, Boolean> isEnabledAndVisible(ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = context.findExisting();
    if (configuration == null) {
      configuration = context.getConfiguration();
    }

    ProgramRunner runner = configuration == null ? null : getRunner(configuration.getConfiguration());
    if (runner == null) {
      return Pair.create(false, false);
    }
    return Pair.create(!ExecutorRegistry.getInstance().isStarting(context.getProject(), myExecutor.getId(), runner.getRunnerId()), true);
  }
}
