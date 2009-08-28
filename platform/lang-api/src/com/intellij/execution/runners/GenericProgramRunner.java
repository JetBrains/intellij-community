package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
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
  /**
   * @see com.intellij.execution.ui.RunContentDescriptor#myContent
   */
  @NonNls public static final String CONTENT_TO_REUSE = "contentToReuse";

  @Nullable
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

  @Nullable
  public SettingsEditor<Settings> getSettingsEditor(final Executor executor, final RunConfiguration configuration) {
    return null;
  }

  public void execute(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) throws ExecutionException {
    execute(executor, environment, null);
  }

  public void execute(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env, @Nullable final Callback callback)
      throws ExecutionException {
    final DataContext dataContext = env.getDataContext();
    final RunProfile profile = env.getRunProfile();

    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final RunContentDescriptor reuseContent =
      ExecutionManager.getInstance(project).getContentManager().getReuseContent(executor, dataContext);

    final RunProfileState state = env.getState(executor);
    if (state == null) {
      return;
    }

    Runnable startRunnable = new Runnable() {
      public void run() {
        try {
          if (project.isDisposed()) return;

          final RunContentDescriptor descriptor =
            doExecute(project, executor, state, reuseContent, env);

          if (callback != null) callback.processStarted(descriptor);

          if (descriptor != null) {
            if (LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_RUNNING) {
              LocalHistory.putSystemLabel(project, executor.getId() + " " + profile.getName());
            }

            ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
            final ProcessHandler processHandler = descriptor.getProcessHandler();
            if (processHandler != null) processHandler.startNotify();
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
  protected abstract RunContentDescriptor doExecute(final Project project, final Executor executor, final RunProfileState state,
                                        final RunContentDescriptor contentToReuse,
                                        final ExecutionEnvironment env) throws ExecutionException;
}
