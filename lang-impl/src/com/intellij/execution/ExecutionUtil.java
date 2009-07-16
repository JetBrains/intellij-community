package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.ide.DataManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public class ExecutionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ExecutionUtil");

  private static final Icon INVALID_CONFIGURATION = IconLoader.getIcon("/runConfigurations/invalidConfigurationLayer.png");

  private ExecutionUtil() {
  }

  @Nullable
  public static ProgramRunner getRunner(final String executorId, final RunnerAndConfigurationSettingsImpl configuration) {
    return RunnerRegistry.getInstance().getRunner(executorId, configuration.getConfiguration());
  }

  public static void executeConfiguration(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettingsImpl configuration, @NotNull final Executor executor, @NotNull final DataContext dataContext) {
    ProgramRunner runner = getRunner(executor.getId(), configuration);
    LOG.assertTrue(runner != null, "Runner MUST not be null!");

    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    LOG.assertTrue(component != null, "component MUST not be null!");
    if (!RunManagerImpl.canRunConfiguration(configuration, executor)) {
      final boolean result = RunDialog.editConfiguration(project, configuration, "Edit configuration", executor.getActionName(), executor.getIcon());
      if (!result) {
        return;
      }

      while (!RunManagerImpl.canRunConfiguration(configuration, executor)) {
        if (0 == Messages.showOkCancelDialog(project, "Configuration is still wrong. Do you want to edit it again?", "Change configuration settings", Messages.getErrorIcon())) {
          final boolean result2 = RunDialog.editConfiguration(project, configuration, "Edit configuration", executor.getActionName(), executor.getIcon());
          if (!result2) {
            return;
          }
        } else {
          break;
        }
      }
    }

    try {
      runner.execute(executor, new ExecutionEnvironment(runner, configuration, dataContext));
    }
    catch (RunCanceledByUserException e) {
      // nothing
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, ExecutionBundle.message("error.running.configuration.with.error.error.message",
                                                                configuration.getName(), e1.getMessage()),
                                        ExecutionBundle.message("run.error.message.title"));
    }
  }

  private static DataContext recreateDataContext(final Project project, final Component component) {
    if (component != null && component.isDisplayable()) return DataManager.getInstance().getDataContext(component);
    return SimpleDataContext.getProjectContext(project);
  }

  public static Icon getConfigurationIcon(final Project project, final RunnerAndConfigurationSettings settings, final boolean invalid) {
    final RunManager runManager = RunManager.getInstance(project);
    RunConfiguration configuration = settings.getConfiguration();
    final Icon icon = settings.getFactory().getIcon(configuration);
    final Icon configurationIcon = runManager.isTemporary(configuration) ? IconLoader.getTransparentIcon(icon, 0.3f) : icon;
    if (invalid) {
      return LayeredIcon.create(configurationIcon, INVALID_CONFIGURATION);
    }

    return configurationIcon;
  }

  public static Icon getConfigurationIcon(final Project project, final RunnerAndConfigurationSettings settings) {
    try {
      settings.checkSettings();
      return getConfigurationIcon(project, settings, false);
    }
    catch (RuntimeConfigurationException ex) {
      return getConfigurationIcon(project, settings, true);
    }
  }

  public static String shortenName(final String name, final int toBeAdded) {
    if (name == null) return "";
    final int symbols = Math.max(10, 20 - toBeAdded);
    if (name.length() < symbols) return name;
    else return name.substring(0, symbols) + "...";
  }

}
