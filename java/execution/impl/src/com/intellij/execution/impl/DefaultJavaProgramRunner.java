// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.statistics.ProgramRunnerUsageCollector;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.*;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.threadDumpParser.ThreadDumpParser;
import com.intellij.threadDumpParser.ThreadState;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.unscramble.ThreadDumpConsoleFactory;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultJavaProgramRunner implements JvmPatchableProgramRunner<RunnerSettings> {
  private static final Logger LOG = Logger.getInstance(DefaultJavaProgramRunner.class);
  private static final String ourWiseThreadDumpProperty = "idea.java.run.wise.thread.dump";

  public static final String DEFAULT_JAVA_RUNNER_ID = "Run";

  public static ProgramRunner<?> getInstance() {
    return ProgramRunner.findRunnerById(DEFAULT_JAVA_RUNNER_ID);
  }

  @Override
  public @NotNull String getRunnerId() {
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

    Project project = environment.getProject();
    ExecutionManager executionManager = ExecutionManager.getInstance(project);
    RunProfile runProfile = environment.getRunProfile();
    StructuredIdeActivity activity = ProgramRunnerUsageCollector.INSTANCE.startExecute(project, this, runProfile);
    if (runProfile instanceof TargetEnvironmentAwareRunProfile &&
        currentState instanceof TargetEnvironmentAwareRunProfileState) {
      executionManager.startRunProfileWithPromise(environment, currentState, (ignored) -> {
        return doExecuteAsync((TargetEnvironmentAwareRunProfileState)currentState, environment).onSuccess((RunContentDescriptor descr) -> {
          ProgramRunnerUsageCollector.INSTANCE.finishExecute(activity, this, runProfile, true);
        });
      });
    }
    else {
      executionManager.startRunProfile(environment, currentState, (ignored) -> doExecute(currentState, environment));
      ProgramRunnerUsageCollector.INSTANCE.finishExecute(activity, this, runProfile, false);
    }
  }

  // cannot be final - overridden in YourKit plugin
  @Override
  public void patch(@NotNull JavaParameters javaParameters, @Nullable RunnerSettings settings, @NotNull RunProfile runProfile, boolean beforeExecution) {
    JavaProgramPatcher.runCustomPatchers(javaParameters, DefaultRunExecutor.getRunExecutorInstance(), runProfile);
  }

  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    ProcessProxy proxy = null;
    if (state instanceof JavaCommandLine) {
      if (!JavaProgramPatcher.patchJavaCommandLineParamsUnderProgress(env.getProject(), 
                                                                      () -> patchJavaCommandLineParams((JavaCommandLine)state, env))){
        return null;
      }
      proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
    }
    return executeJavaState(state, env, proxy);
  }

  private void patchJavaCommandLineParams(@NotNull JavaCommandLine state, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    final JavaParameters parameters = state.getJavaParameters();
    patch(parameters, env.getRunnerSettings(), env.getRunProfile(), true);

    if (Registry.is("execution.java.always.debug") && DebuggerSettings.getInstance().ALWAYS_DEBUG) {
      ParametersList parametersList = parameters.getVMParametersList();
      if (!ContainerUtil.exists(parametersList.getList(), s -> s.startsWith("-agentlib:jdwp"))) {
        parametersList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y");
      } 
    }
  }

  protected @NotNull Promise<@Nullable RunContentDescriptor> doExecuteAsync(@NotNull TargetEnvironmentAwareRunProfileState state,
                                                                            @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    boolean isLocal = !((TargetEnvironmentAwareRunProfile)env.getRunProfile()).needPrepareTarget();
    if (!isLocal && !isExecutorSupportedOnTarget(env)) {
      throw new ExecutionException(
        ExecutionBundle.message("run.configuration.action.is.supported.for.local.machine.only", env.getExecutor().getActionName())
      );
    }

    return state.prepareTargetToCommandExecution(env, LOG,"Failed to execute java run configuration async", () -> {
      @Nullable ProcessProxy proxy = null;
      if (state instanceof JavaCommandLine) {
        patchJavaCommandLineParams((JavaCommandLine)state, env);
        if (isLocal) {
          proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
        }
      }

      return executeJavaState(state, env, proxy);
    });
  }

  /**
   * Running configurations under the profiler and with the coverage is not yet
   * supported for execution on targets other than the local machine.
   */
  private static boolean isExecutorSupportedOnTarget(@NotNull ExecutionEnvironment env) {
    Executor executor = env.getExecutor();
    return env.getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest || executor.isSupportedOnTarget();
  }

  private @Nullable RunContentDescriptor executeJavaState(@NotNull RunProfileState state,
                                                          @NotNull ExecutionEnvironment env,
                                                          @Nullable ProcessProxy proxy) throws ExecutionException {
    ExecutionResult executionResult = state.execute(env.getExecutor(), this);
    if (proxy != null) {
      ProcessHandler handler = executionResult != null ? executionResult.getProcessHandler() : null;
      if (handler != null) {
        proxy.attach(handler);
        handler.addProcessListener(new ProcessListener() {
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

    if (executionResult == null) {
      return null;
    }

    AtomicReference<RunContentDescriptor> result = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
      if (!(state instanceof JavaCommandLineState) || ((JavaCommandLineState)state).shouldAddJavaProgramRunnerActions()) {
        addDefaultActions(contentBuilder, executionResult, state instanceof JavaCommandLine);
      }
      result.set(contentBuilder.showRunContent(env.getContentToReuse()));
    });
    return result.get();
  }

  private static void addDefaultActions(@NotNull RunContentBuilder contentBuilder,
                                        @NotNull ExecutionResult executionResult,
                                        boolean isJavaCommandLine) {
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    ProcessHandler processHandler = executionResult.getProcessHandler();
    assert processHandler != null : executionResult;
    final ControlBreakAction controlBreakAction = new ControlBreakAction();
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(final @NotNull ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    if (isJavaCommandLine) {
      AttachDebuggerAction.add(contentBuilder, processHandler);
    }
  }

  private abstract static class ProxyBasedAction extends AnAction {
    protected ProxyBasedAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
      super(text, description, icon);
    }

    protected ProcessHandler getProcessHandler(@NotNull AnActionEvent e) {
      RunContentDescriptor contentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
      return contentDescriptor == null ? null : contentDescriptor.getProcessHandler();
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public final void update(@NotNull AnActionEvent event) {
      ProcessHandler processHandler = getProcessHandler(event);
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(processHandler);
      boolean available = proxy != null && available(proxy);
      Presentation presentation = event.getPresentation();
      if (!available) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        presentation.setVisible(true);
        presentation.setEnabled(!processHandler.isProcessTerminated());
      }
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      ProcessHandler processHandler = getProcessHandler(e);
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(processHandler);
      if (proxy != null) {
        perform(e, proxy, processHandler);
      }
    }

    protected abstract boolean available(ProcessProxy proxy);

    protected abstract void perform(AnActionEvent e, ProcessProxy proxy, ProcessHandler handler);
  }

  static final class ControlBreakAction extends ProxyBasedAction implements ActionRemoteBehaviorSpecification.Disabled {
    private final ExecutorService myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Thread Dumper", 1);

    public ControlBreakAction() {
      super(ExecutionBundle.message("run.configuration.dump.threads.action.name"), null, AllIcons.Actions.Dump);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendBreak();
    }

    @Override
    protected void perform(AnActionEvent event, ProcessProxy proxy, ProcessHandler processHandler) {
      Project project = event.getProject();
      if (project == null) {
        return;
      }
      RunnerContentUi runnerContentUi = event.getData(RunnerContentUi.KEY);
      if (processHandler instanceof BaseProcessHandler && runnerContentUi != null) {
        String pid = String.valueOf(((BaseProcessHandler<?>)processHandler).getProcess().pid());
        RunTab runTab = event.getData(RunTab.KEY);
        GlobalSearchScope scope =
          runTab instanceof RunContentBuilder ? ((RunContentBuilder)runTab).getSearchScope() : GlobalSearchScope.allScope(project);
        if (!JavaDebuggerAttachUtil.getAttachedPids(project).contains(pid)) {
          myExecutor.execute(() -> {
            String dump = ThreadDumpProvider.dump(pid);
            if (dump != null) {
              List<ThreadState> threads = ThreadDumpParser.parse(dump);
              ApplicationManager.getApplication().invokeLater(
                () -> DebuggerUtilsEx.addThreadDump(project, threads, runnerContentUi.getRunnerLayoutUi(), scope),
                ModalityState.nonModal());
            }
            else {
              dumpWithBreak(proxy, project, processHandler);
            }
          });
          return;
        }
      }
      dumpWithBreak(proxy, project, processHandler);
    }

    private static void dumpWithBreak(ProcessProxy proxy, Project project, ProcessHandler processHandler) {
      boolean wise = Boolean.getBoolean(ourWiseThreadDumpProperty);
      WiseDumpThreadsListener wiseListener = wise ? new WiseDumpThreadsListener(project, processHandler) : null;

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
      myProcessHandler.addProcessListener(new ProcessListener() {
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

      getTemplatePresentation().putClientProperty(RunTab.PREFERRED_PLACE, PreferredPlace.MORE_GROUP);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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
                if (((PidRemoteConnection)connection).getPid().equals(String.valueOf(myProcessHandler.getProcess().pid()))) {
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
      // disabled on macos because of IDEA-252760
      if (Registry.is("debugger.attach.to.process.action") && processHandler instanceof BaseProcessHandler && !SystemInfo.isMac) {
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
      () -> AnalyzeStacktraceUtil.addConsole(project, factory, title, out), ModalityState.nonModal());
  }

  static final class SoftExitAction extends ProxyBasedAction {
    SoftExitAction() {
      super(ExecutionBundle.message("run.configuration.exit.action.name"), null, AllIcons.Actions.Exit);
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendStop();
    }

    @Override
    protected void perform(AnActionEvent e, ProcessProxy proxy, ProcessHandler processHandler) {
      processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, Boolean.TRUE);
      proxy.sendStop();
    }
  }
}
