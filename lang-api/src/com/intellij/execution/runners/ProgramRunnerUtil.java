package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ProgramRunnerUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.runners.ProgramRunnerUtil");

  private ProgramRunnerUtil() {
  }

  public static void execute(@NotNull final Executor executor,
                             @NotNull final ProgramRunner runner,
                             @NotNull final RunnerAndConfigurationSettings settings,
                             @NotNull final DataContext dataContext) throws ExecutionException {
    runner.execute(executor, settings.getConfiguration(), dataContext, settings.getRunnerSettings(runner),
            settings.getConfigurationSettings(runner));
  }

  public static void handleExecutionError(final Project project, final RunProfile runProfile, final ExecutionException e) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    String message = ExecutionBundle.message("error.running.configuration.with.error.error.message", runProfile.getName(), e.getMessage());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.assertTrue(false, message);
    }
    else {
      Messages.showErrorDialog(project, message, ExecutionBundle.message("run.error.message.title"));
    }
  }

}
