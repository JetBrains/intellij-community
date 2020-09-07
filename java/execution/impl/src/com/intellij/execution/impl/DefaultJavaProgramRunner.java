// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.unscramble.ThreadDumpConsoleFactory;
import com.intellij.unscramble.ThreadDumpParser;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultJavaProgramRunner implements JvmPatchableProgramRunner<RunnerSettings> {
  private static final Logger LOG = Logger.getInstance(DefaultJavaProgramRunner.class);
  private final static String ourWiseThreadDumpProperty = "idea.java.run.wise.thread.dump";

  public static final String DEFAULT_JAVA_RUNNER_ID = "Run";

  public static ProgramRunner<?> getInstance() {
    return ProgramRunner.findRunnerById(DEFAULT_JAVA_RUNNER_ID);
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return DEFAULT_JAVA_RUNNER_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) &&
           profile instanceof ModuleRunProfile &&
           !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction);
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    RunProfileState currentState = environment.getState();
    if (currentState == null) {
      return;
    }

    ExecutionManager executionManager = ExecutionManager.getInstance(environment.getProject());
    executionManager
      .executePreparationTasks(environment, currentState)
      .onSuccess(__ -> {
        executionManager.startRunProfile(environment, currentState, (ignored) -> {
          return doExecute(currentState, environment);
        });
      });
  }

  // cannot be final - overridden in YourKit plugin
  @Override
  public void patch(@NotNull JavaParameters javaParameters, @Nullable RunnerSettings settings, @NotNull RunProfile runProfile, boolean beforeExecution) {
    JavaProgramPatcher.runCustomPatchers(javaParameters, DefaultRunExecutor.getRunExecutorInstance(), runProfile);
  }

  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    boolean shouldAddDefaultActions = true;
    if (state instanceof JavaCommandLine) {
      final JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      patch(parameters, env.getRunnerSettings(), env.getRunProfile(), true);

      if (Registry.is("execution.java.always.debug") && DebuggerSettings.getInstance().ALWAYS_DEBUG) {
        ParametersList parametersList = parameters.getVMParametersList();
        if (parametersList.getList().stream().noneMatch(s -> s.startsWith("-agentlib:jdwp"))) {
          parametersList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y");
        }
      }

      ProcessProxy proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
      executionResult = state.execute(env.getExecutor(), this);
      if (proxy != null) {
        ProcessHandler handler = executionResult != null ? executionResult.getProcessHandler() : null;
        if (handler != null) {
          proxy.attach(handler);
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              proxy.destroy();
              handler.removeProcessListener(this);
            }
          });
        }
        else {
          proxy.destroy();
        }
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

    RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
    if (shouldAddDefaultActions) {
      addDefaultActions(contentBuilder, executionResult, state instanceof JavaCommandLine);
    }
    return contentBuilder.showRunContent(env.getContentToReuse());
  }

  private static void addDefaultActions(@NotNull RunContentBuilder contentBuilder,
                                        @NotNull ExecutionResult executionResult,
                                        boolean isJavaCommandLine) {
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    ProcessHandler processHandler = executionResult.getProcessHandler();
    assert processHandler != null : executionResult;
    final ControlBreakAction controlBreakAction = new ControlBreakAction(processHandler, contentBuilder.getSearchScope());
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    contentBuilder.addAction(controlBreakAction);
    if (isJavaCommandLine) {
      AttachDebuggerAction.add(contentBuilder, processHandler);
    }
    contentBuilder.addAction(new SoftExitAction(processHandler));
  }

  private abstract static class ProxyBasedAction extends AnAction {
    protected final ProcessHandler myProcessHandler;

    protected ProxyBasedAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon, ProcessHandler processHandler) {
      super(text, description, icon);
      myProcessHandler = processHandler;
    }

    @Override
    public final void update(@NotNull AnActionEvent event) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      boolean available = proxy != null && available(proxy);
      Presentation presentation = event.getPresentation();
      if (!available) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        presentation.setVisible(true);
        presentation.setEnabled(!myProcessHandler.isProcessTerminated());
      }
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        perform(e, proxy);
      }
    }

    protected abstract boolean available(ProcessProxy proxy);

    protected abstract void perform(AnActionEvent e, ProcessProxy proxy);
  }

  protected static final class ControlBreakAction extends ProxyBasedAction {
    private final GlobalSearchScope mySearchScope;
    private final ExecutorService myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Thread Dumper", 1);

    public ControlBreakAction(final ProcessHandler processHandler, GlobalSearchScope searchScope) {
      super(ExecutionBundle.message("run.configuration.dump.threads.action.name"), null, AllIcons.Actions.Dump, processHandler);
      mySearchScope = searchScope;
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendBreak();
    }

    @Override
    protected void perform(AnActionEvent event, ProcessProxy proxy) {
      Project project = event.getProject();
      if (project == null) {
        return;
      }
      RunnerContentUi runnerContentUi = event.getData(RunnerContentUi.KEY);
      if (Registry.is("execution.dump.threads.using.attach") && myProcessHandler instanceof BaseProcessHandler && runnerContentUi != null) {
        String pid = String.valueOf(OSProcessUtil.getProcessID(((BaseProcessHandler<?>)myProcessHandler).getProcess()));
        if (!JavaDebuggerAttachUtil.getAttachedPids(project).contains(pid)) {
          myExecutor.execute(() -> {
            VirtualMachine vm = null;
            try {
              vm = JavaDebuggerAttachUtil.attachVirtualMachine(pid);
              InputStream inputStream = (InputStream)vm.getClass()
                .getMethod("remoteDataDump", Object[].class)
                .invoke(vm, new Object[]{ArrayUtil.EMPTY_OBJECT_ARRAY});
              String text;
              try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                text = StreamUtil.readText(reader);
              }
              List<ThreadState> threads = ThreadDumpParser.parse(text);
              ApplicationManager.getApplication().invokeLater(
                () -> DebuggerUtilsEx.addThreadDump(project, threads, runnerContentUi.getRunnerLayoutUi(), mySearchScope),
                ModalityState.NON_MODAL);
            }
            catch (AttachNotSupportedException e) {
              LOG.debug(e);
              dumpWithBreak(proxy, project);
            }
            catch (Exception e) {
              LOG.warn(e);
              dumpWithBreak(proxy, project);
            }
            finally {
              if (vm != null) {
                try {
                  vm.detach();
                }
                catch (IOException ignored) {
                }
              }
            }
          });
          return;
        }
      }
      dumpWithBreak(proxy, project);
    }

    private void dumpWithBreak(ProcessProxy proxy, Project project) {
      boolean wise = Boolean.getBoolean(ourWiseThreadDumpProperty);
      WiseDumpThreadsListener wiseListener = wise ? new WiseDumpThreadsListener(project, myProcessHandler) : null;

      proxy.sendBreak();

      if (wiseListener != null) {
        wiseListener.after();
      }
    }
  }

  protected static final class AttachDebuggerAction extends DumbAwareAction {
    private final AtomicBoolean myEnabled = new AtomicBoolean();
    private final AtomicReference<XDebugSession> myAttachedSession = new AtomicReference<>();
    private final BaseProcessHandler<?> myProcessHandler;
    private MessageBusConnection myConnection = null;

    public AttachDebuggerAction(BaseProcessHandler<?> processHandler) {
      super(ExecutionBundle.message("run.configuration.attach.debugger.action.name"), null, AllIcons.Debugger.AttachToProcess);

      myProcessHandler = processHandler;
      myProcessHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            // 1 second delay to allow jvm to start correctly
            AppExecutorUtil.getAppScheduledExecutorService()
              .schedule(() -> myEnabled.set(!myProcessHandler.isProcessTerminating() && !myProcessHandler.isProcessTerminated() &&
                                            JavaDebuggerAttachUtil.canAttach(myProcessHandler)),
                        1, TimeUnit.SECONDS);
          }
        }

        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (myConnection != null) {
            myConnection.disconnect();
          }
          myProcessHandler.removeProcessListener(this);
          XDebugSession attachedSession = myAttachedSession.getAndSet(null);
          if (attachedSession != null) {
            attachedSession.stop();
          }
        }
      });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null && myConnection == null) {
        myConnection = project.getMessageBus().connect();
        myConnection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
          @Override
          public void processStarted(@NotNull XDebugProcess debugProcess) {
            processEvent(debugProcess, true);
          }

          @Override
          public void processStopped(@NotNull XDebugProcess debugProcess) {
            processEvent(debugProcess, false);
          }

          void processEvent(@NotNull XDebugProcess debugProcess, boolean started) {
            if (debugProcess instanceof JavaDebugProcess) {
              RemoteConnection connection = ((JavaDebugProcess)debugProcess).getDebuggerSession().getProcess().getConnection();
              if (connection instanceof PidRemoteConnection) {
                if (((PidRemoteConnection)connection).getPid()
                  .equals(String.valueOf(OSProcessUtil.getProcessID(myProcessHandler.getProcess())))) {
                  myAttachedSession.set(started ? debugProcess.getSession() : null);
                }
              }
            }
          }
        });
      }
      if (myAttachedSession.get() != null || myProcessHandler.isProcessTerminated()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      e.getPresentation().setEnabledAndVisible(Boolean.TRUE.equals(myEnabled.get()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JavaDebuggerAttachUtil.attach(myProcessHandler, e.getProject());
    }

    public static void add(RunContentBuilder contentBuilder, ProcessHandler processHandler) {
      if (Registry.is("debugger.attach.to.process.action") && processHandler instanceof BaseProcessHandler) {
        contentBuilder.addAction(new AttachDebuggerAction((BaseProcessHandler<?>)processHandler));
      }
    }
  }

  private static final class WiseDumpThreadsListener {
    private final Project myProject;
    private final ProcessHandler myProcessHandler;
    private final CapturingProcessAdapter myListener;

    WiseDumpThreadsListener(Project project, ProcessHandler processHandler) {
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
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        if (myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating()) return;
        List<ThreadState> threadStates = null;
        final long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 1000) {
          String stdout = myListener.getOutput().getStdout();
          threadStates = ThreadDumpParser.parse(stdout);
          if (threadStates.isEmpty()) {
            TimeoutUtil.sleep(50);
            threadStates = null;
            continue;
          }
          break;
        }
        myProcessHandler.removeProcessListener(myListener);
        if (threadStates != null && !threadStates.isEmpty()) {
          showThreadDump(myListener.getOutput().getStdout(), threadStates, myProject);
        }
      });
    }
  }

  private static void showThreadDump(String out, List<ThreadState> states, Project project) {
    AnalyzeStacktraceUtil.ConsoleFactory factory = states.size() > 1 ? new ThreadDumpConsoleFactory(project, states) : null;
    String title = JavaCompilerBundle.message("tab.title.thread.dump", DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis()));
    ApplicationManager.getApplication().invokeLater(
      () -> AnalyzeStacktraceUtil.addConsole(project, factory, title, out), ModalityState.NON_MODAL);
  }

  protected static final class SoftExitAction extends ProxyBasedAction {
    public SoftExitAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.exit.action.name"), null, AllIcons.Actions.Exit, processHandler);
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendStop();
    }

    @Override
    protected void perform(AnActionEvent e, ProcessProxy proxy) {
      myProcessHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, Boolean.TRUE);
      proxy.sendStop();
    }
  }
}