package com.intellij.execution.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategyImpl;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.Messages;

public class RunContextAction extends BaseRunConfigurationAction {
  private final JavaProgramRunner myRunner;

  public RunContextAction(final JavaProgramRunner runner) {
    super(ExecutionBundle.message("perform.action.with.context.configuration.action.name", runner.getInfo().getStartActionText()), null, runner.getInfo().getIcon());
    myRunner = runner;
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
    try {
      RunStrategyImpl.getInstance().execute(configuration.getConfiguration(), context.getDataContext(), myRunner,
                                        configuration.getRunnerSettings(myRunner), configuration.getConfigurationSettings(myRunner));
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(context.getProject(), e.getMessage(), ExecutionBundle.message("error.common.title"));
    }
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    presentation.setText(myRunner.getInfo().getStartActionText() + actionText, true);
  }
}
