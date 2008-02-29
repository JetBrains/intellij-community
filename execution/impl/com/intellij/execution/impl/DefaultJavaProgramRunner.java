package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author spleaner
 */
public class DefaultJavaProgramRunner extends JavaPatchableProgramRunner {
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) &&
           profile instanceof ModuleRunProfile &&
           !(profile instanceof RemoteConfiguration);
  }

  public JDOMExternalizable createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getSettingsEditor(final Executor executor, RunConfiguration configuration) {
    return null;
  }

  public void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException {
  }

  public void checkConfiguration(final RunnerSettings settings, final ConfigurationPerRunnerSettings configurationPerRunnerSettings)
      throws RuntimeConfigurationException {
  }

  public void onProcessStarted(final RunnerSettings settings, final ExecutionResult executionResult) {
  }

  public AnAction[] createActions(ExecutionResult executionResult) {
    return AnAction.EMPTY_ARRAY;
  }

  @Nullable
  public RunContentDescriptor doExecute(final Executor executor,
                                        final RunProfileState state,
                                        final RunProfile runProfile,
                                        final Project project,
                                        final RunContentDescriptor contentToReuse,
                                        RunnerSettings settings,
                                        ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    if (state instanceof JavaCommandLine) {
      patch(((JavaCommandLine)state).getJavaParameters(), state.getRunnerSettings(), true);
      final ProcessProxy proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
      executionResult = state.execute(executor, this);
      if (proxy != null && executionResult != null) {
        proxy.attach(executionResult.getProcessHandler());
      }
    }
    else {
      executionResult = state.execute(executor, this);
    }

    if (executionResult == null) {
      return null;
    }

    onProcessStarted(settings, executionResult);

    final RunContentBuilder contentBuilder = new RunContentBuilder(project, this, executor);
    contentBuilder.setExecutionResult(executionResult);
    contentBuilder.setRunProfile(runProfile, settings, configurationSettings);
    customizeContent(contentBuilder);

    RunContentDescriptor runContent = contentBuilder.showRunContent(contentToReuse);

    AnAction[] actions = createActions(contentBuilder.getExecutionResult());

    for (AnAction action : actions) {
      contentBuilder.addAction(action);
    }

    return runContent;
  }

  protected static void customizeContent(final RunContentBuilder contentBuilder) {
    final ExecutionResult executionResult = contentBuilder.getExecutionResult();
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    final ControlBreakAction controlBreakAction = new ControlBreakAction(contentBuilder.getProcessHandler());
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      final ProcessHandler processHandler = executionResult.getProcessHandler();
      processHandler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    contentBuilder.addAction(controlBreakAction);
    contentBuilder.addAction(new SoftExitAction(contentBuilder.getProcessHandler()));
  }


  private abstract static class LauncherBasedAction extends AnAction {
    protected final ProcessHandler myProcessHandler;

    protected LauncherBasedAction(String text, String description, Icon icon, ProcessHandler processHandler) {
      super(text, description, icon);
      myProcessHandler = processHandler;
    }

    public void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      if (ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler) == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }
      presentation.setVisible(true);
      presentation.setEnabled(!myProcessHandler.isProcessTerminated());
    }
  }

  protected static class ControlBreakAction extends LauncherBasedAction {
    public ControlBreakAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.dump.threads.action.name"), null, IconLoader.getIcon("/actions/dump.png"),
            processHandler);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    public void actionPerformed(final AnActionEvent e) {
      ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler).sendBreak();
    }
  }

  protected static class SoftExitAction extends LauncherBasedAction {
    public SoftExitAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.exit.action.name"), null, IconLoader.getIcon("/actions/exit.png"), processHandler);
    }

    public void actionPerformed(final AnActionEvent e) {
      ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler).sendStop();
    }
  }

  @NotNull
  public String getRunnerId() {
    return "Run";
  }
}
