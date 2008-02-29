package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunContextAction extends BaseRunConfigurationAction {
  private Executor myExecutor;

  public RunContextAction(@NotNull final Executor executor) {
    super(ExecutionBundle.message("perform.action.with.context.configuration.action.name", executor.getStartActionText()), null,
          executor.getIcon());
    myExecutor = executor;
  }

  protected void perform(final ConfigurationContext context) {
    RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
    final RunManagerEx runManager = context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) return;
      runManager.setTemporaryConfiguration(configuration);
    }
    runManager.setActiveConfiguration(configuration);

    final ProgramRunner runner = getRunner(myExecutor.getId(), configuration);
    if (runner != null) {
      try {
      runner.execute(myExecutor, configuration.getConfiguration(), context.getDataContext(), configuration.getRunnerSettings(runner),
                       configuration.getConfigurationSettings(runner));
      }
      catch (ExecutionException e) {
        Messages.showErrorDialog(context.getProject(), e.getMessage(), ExecutionBundle.message("error.common.title"));
      }
    }
  }

  @Nullable
  private static ProgramRunner getRunner(final String executorId, final RunnerAndConfigurationSettingsImpl selectedConfiguration) {
    return RunnerRegistry.getInstance().getRunner(executorId, selectedConfiguration.getConfiguration());
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    presentation.setText(myExecutor.getStartActionText() + actionText, true);

    RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
    if (configuration == null) {
      configuration = context.getConfiguration();
    }

    final boolean b = configuration != null && getRunner(myExecutor.getId(), configuration) != null;
    presentation.setEnabled(b);
    presentation.setVisible(b);
  }
}
