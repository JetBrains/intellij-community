package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class GenericProgramRunner<Settings extends JDOMExternalizable> implements ProgramRunner<Settings> {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.runners.GenericProgramRunner");

  /**
   * @see com.intellij.execution.ui.RunContentDescriptor#myContent
   */
  @NonNls public static final String CONTENT_TO_REUSE = "contentToReuse";

  public Settings createConfigurationData(final ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  public void checkConfiguration(final RunnerSettings settings, final ConfigurationPerRunnerSettings configurationPerRunnerSettings)
    throws RuntimeConfigurationException {
  }

  public void onProcessStarted(final RunnerSettings settings, final ExecutionResult executionResult) {
  }

  public AnAction[] createActions(final ExecutionResult executionResult) {
    return AnAction.EMPTY_ARRAY;
  }

  public SettingsEditor<Settings> getSettingsEditor(final Executor executor, final RunConfiguration configuration) {
    return null;
  }

  public void execute(@NotNull final Executor executor, @NotNull final RunProfile profile, @NotNull final DataContext dataContext, @Nullable final RunnerSettings settings,
                      final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    execute(executor, profile, dataContext, settings, configurationSettings, null);
  }

  public void execute(@NotNull final Executor executor, @NotNull final RunProfile profile, @NotNull final DataContext dataContext,
                      @Nullable final RunnerSettings settings,
                      @Nullable final ConfigurationPerRunnerSettings configurationSettings,
                      @Nullable final Callback callback) throws ExecutionException {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final RunContentDescriptor reuseContent =
      ExecutionManager.getInstance(project).getContentManager().getReuseContent(executor, dataContext);

    final RunProfileState state = profile.getState(dataContext, executor, settings, configurationSettings);
    if (state == null) {
      return;
    }

    Runnable startRunnable = new Runnable() {
      public void run() {
        try {
          if (project.isDisposed()) return;
          
          final RunContentDescriptor descriptor =
            doExecute(executor, state, profile, project, reuseContent, settings, configurationSettings);

          if (callback != null) callback.processStarted(descriptor);

          if (descriptor != null) {
            if (LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_RUNNING) {
              LocalHistory.putSystemLabel(project, executor.getId() + " " + profile.getName());
            }

            ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
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
  protected abstract RunContentDescriptor doExecute(final Executor executor, final RunProfileState state,
                                        final RunProfile runProfile,
                                        final Project project,
                                        final RunContentDescriptor contentToReuse,
                                        final RunnerSettings settings,
                                        final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException;
}
