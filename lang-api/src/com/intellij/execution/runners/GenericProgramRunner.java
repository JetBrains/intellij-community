package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author spleaner
 */
public abstract class GenericProgramRunner<Settings extends JDOMExternalizable, Parameters> implements ProgramRunner<Settings, Parameters> {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.runners.GenericProgramRunner");

  private static final Icon ICON = IconLoader.getIcon("/actions/execute.png");
  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/toolWindowRun.png");
  private static final Icon DISABLED_ICON = IconLoader.getIcon("/process/disabledRun.png");

  /**
   * @see com.intellij.execution.ui.RunContentDescriptor#myContent
   */
  @NonNls public static final String CONTENT_TO_REUSE = "contentToReuse";

  public static final RunnerInfo DEFAULT_RUNNER_INFO = new RunnerInfo(ToolWindowId.RUN, ExecutionBundle.message("standard.runner.description"),
                                                              ICON, TOOLWINDOW_ICON, ToolWindowId.RUN, "ideaInterface.run") {


    public String getRunContextActionId() {
      return "RunClass";
    }

    public String getStartActionText() {
      return ExecutionBundle.message("default.runner.start.action.text");
    }

    public Icon getDisabledIcon() {
      return DISABLED_ICON;
    }

    public Icon getEnabledIcon() {
      return TOOLWINDOW_ICON;
    }
  };



  public void execute(@NotNull final RunProfile profile, @NotNull final DataContext dataContext,
                      @NotNull final RunnerSettings settings,
                      @NotNull final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    execute(profile, dataContext, settings, configurationSettings, null);
  }

  public void execute(@NotNull final RunnerAndConfigurationSettings settings, @NotNull final DataContext dataContext)
    throws ExecutionException {
    execute(settings.getConfiguration(), dataContext, settings.getRunnerSettings(this), settings.getConfigurationSettings(this));
  }

  public void execute(@NotNull final RunProfile profile, @NotNull final DataContext dataContext,
                      @NotNull final RunnerSettings settings,
                      @NotNull final ConfigurationPerRunnerSettings configurationSettings,
                      @Nullable final Callback callback) throws ExecutionException {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final RunContentDescriptor reuseContent =
      ExecutionManager.getInstance(project).getContentManager().getReuseContent(this, dataContext);

    final RunProfileState state = profile.getState(dataContext, getInfo(), settings, configurationSettings);
    if (state == null) {
      return;
    }

    Runnable startRunnable = new Runnable() {
      public void run() {
        try {
          final RunContentDescriptor descriptor =
            doExecute(state, profile, project, reuseContent, settings, configurationSettings);

          if (callback != null) callback.processStarted(descriptor);

          if (descriptor != null) {
            if (LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_RUNNING) {
              LocalHistory.putSystemLabel(project, getInfo().getId() + " " + profile.getName());
            }

            ExecutionManager.getInstance(project).getContentManager().showRunContent(getInfo(), descriptor);
            descriptor.getProcessHandler().startNotify();
          }
        }
        catch (ExecutionException e) {
          ProgramRunnerUtil.handleExecutionError(project, profile, e);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      startRunnable.run();
    }
    else {
      ExecutionManager.getInstance(project).compileAndRun(startRunnable, profile, state);
    }
  }

  @Nullable
  protected abstract RunContentDescriptor doExecute(final RunProfileState state,
                                        final RunProfile runProfile,
                                        final Project project,
                                        final RunContentDescriptor contentToReuse,
                                        final RunnerSettings settings,
                                        final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException;
}
