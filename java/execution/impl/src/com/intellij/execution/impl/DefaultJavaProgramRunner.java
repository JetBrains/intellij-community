/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.unscramble.ThreadDumpConsoleFactory;
import com.intellij.unscramble.ThreadDumpParser;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author spleaner
 */
public class DefaultJavaProgramRunner extends JavaPatchableProgramRunner {
  private final static String ourWiseThreadDumpProperty = "idea.java.run.wise.thread.dump";

  @NonNls public static final String DEFAULT_JAVA_RUNNER_ID = "Run";

  @Override
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) &&
           profile instanceof ModuleRunProfile &&
           !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction);
  }

  @Override
  public void patch(JavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, final boolean beforeExecution) throws ExecutionException {
    runCustomPatchers(javaParameters, DefaultRunExecutor.getRunExecutorInstance(), runProfile);
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    boolean shouldAddDefaultActions = true;
    if (state instanceof JavaCommandLine) {
      final JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      patch(parameters, env.getRunnerSettings(), env.getRunProfile(), true);
      final ProcessProxy proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
      executionResult = state.execute(env.getExecutor(), this);
      if (proxy != null && executionResult != null) {
        proxy.attach(executionResult.getProcessHandler());
      }
      if (state instanceof JavaCommandLineState && !((JavaCommandLineState)state).shouldAddJavaProgramRunnerActions()) {
        shouldAddDefaultActions = false;
      }
    }
    else {
      executionResult = state.execute(env.getExecutor(), this);
    }

    if (executionResult == null) {
      return null;
    }

    onProcessStarted(env.getRunnerSettings(), executionResult);

    final RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
    if (shouldAddDefaultActions) {
      addDefaultActions(contentBuilder, executionResult);
    }
    return contentBuilder.showRunContent(env.getContentToReuse());
  }

  private static void addDefaultActions(@NotNull RunContentBuilder contentBuilder, @NotNull ExecutionResult executionResult) {
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    final ControlBreakAction controlBreakAction = new ControlBreakAction(executionResult.getProcessHandler());
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      final ProcessHandler processHandler = executionResult.getProcessHandler();
      assert processHandler != null : executionResult;
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    contentBuilder.addAction(controlBreakAction);
    contentBuilder.addAction(new SoftExitAction(executionResult.getProcessHandler()));
  }

  private abstract static class LauncherBasedAction extends AnAction {
    protected final ProcessHandler myProcessHandler;

    protected LauncherBasedAction(String text, String description, Icon icon, ProcessHandler processHandler) {
      super(text, description, icon);
      myProcessHandler = processHandler;
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      if (!isVisible()) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }
      presentation.setVisible(true);
      presentation.setEnabled(!myProcessHandler.isProcessTerminated());
    }

    protected boolean isVisible() {
      return ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler) != null;
    }
  }

  protected static class ControlBreakAction extends LauncherBasedAction {
    public ControlBreakAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.dump.threads.action.name"), null, AllIcons.Actions.Dump,
            processHandler);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean isVisible() {
      return super.isVisible() && ProcessProxyFactory.getInstance().isBreakGenLibraryAvailable();
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        final WiseDumpThreadsListener wiseListener = Boolean.TRUE.equals(Boolean.getBoolean(ourWiseThreadDumpProperty)) ?
                                                     new WiseDumpThreadsListener(CommonDataKeys.PROJECT.getData(e.getDataContext()), myProcessHandler) : null;

        proxy.sendBreak();

        if (wiseListener != null) {
          wiseListener.after();
        }
      }
    }
  }

  private static class WiseDumpThreadsListener {
    private final Project myProject;
    private final ProcessHandler myProcessHandler;
    private final CapturingProcessAdapter myListener;

    public WiseDumpThreadsListener(final Project project, final ProcessHandler processHandler) {
      myProject = project;
      myProcessHandler = processHandler;

      myListener = new CapturingProcessAdapter();
      myProcessHandler.addProcessListener(myListener);
    }

    public void after() {
      if (myProject == null) {
        myProcessHandler.removeProcessListener(myListener);
        return;
      }
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          if (myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating()) return;
          List<ThreadState> threadStates = null;
          final long start = System.currentTimeMillis();
          while ((System.currentTimeMillis() - start) < 1000) {
            final String stdout = myListener.getOutput().getStdout();
            threadStates = ThreadDumpParser.parse(stdout);
            if (threadStates == null || threadStates.isEmpty()) {
              TimeoutUtil.sleep(50);
              threadStates = null;
              continue;
            }
            break;
          }
          myProcessHandler.removeProcessListener(myListener);
          if (threadStates != null && ! threadStates.isEmpty()) {
            showThreadDump(myListener.getOutput().getStdout(), threadStates);
          }
        }

      });
    }

    private void showThreadDump(final String out, final List<ThreadState> threadStates) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          AnalyzeStacktraceUtil.addConsole(myProject, threadStates.size() > 1 ?
                                                    new ThreadDumpConsoleFactory(myProject, threadStates) : null,
                                           "<Stacktrace> " + DateFormatUtil.formatDateTime(System.currentTimeMillis()), out);
        }
      }, ModalityState.NON_MODAL);
    }
  }

  protected static class SoftExitAction extends LauncherBasedAction {
    public SoftExitAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.exit.action.name"), null, AllIcons.Actions.Exit, processHandler);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        proxy.sendStop();
      }
    }
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return DEFAULT_JAVA_RUNNER_ID;
  }

  public static ProgramRunner getInstance() {
    return RunnerRegistry.getInstance().findRunnerById(DEFAULT_JAVA_RUNNER_ID);
  }
}
