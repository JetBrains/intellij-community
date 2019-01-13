// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.unscramble.ThreadDumpConsoleFactory;
import com.intellij.unscramble.ThreadDumpParser;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author spleaner
 */
public class DefaultJavaProgramRunner extends JavaPatchableProgramRunner {
  private static final Logger LOG = Logger.getInstance(DefaultJavaProgramRunner.class);
  private final static String ourWiseThreadDumpProperty = "idea.java.run.wise.thread.dump";

  public static final String DEFAULT_JAVA_RUNNER_ID = "Run";

  public static ProgramRunner getInstance() {
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

  @SuppressWarnings("RedundantThrows")
  @Override
  public void patch(JavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, boolean beforeExecution) throws ExecutionException {
    runCustomPatchers(javaParameters, DefaultRunExecutor.getRunExecutorInstance(), runProfile);
  }

  @Override
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

    onProcessStarted(env.getRunnerSettings(), executionResult);

    final RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
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

    protected ProxyBasedAction(String text, String description, Icon icon, ProcessHandler processHandler) {
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

  protected static class ControlBreakAction extends ProxyBasedAction {
    private final GlobalSearchScope mySearchScope;

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
        // try vm attach first
        VirtualMachine vm = null;
        try {
          String pid = String.valueOf(OSProcessUtil.getProcessID(((BaseProcessHandler)myProcessHandler).getProcess()));
          if (!JavaDebuggerAttachUtil.getAttachedPids(project).contains(pid)) {
            vm = JavaDebuggerAttachUtil.attachVirtualMachine(pid);
            InputStream inputStream = (InputStream)vm.getClass().getMethod("remoteDataDump", Object[].class)
              .invoke(vm, new Object[]{ArrayUtil.EMPTY_OBJECT_ARRAY});
            String text = StreamUtil.readText(inputStream, CharsetToolkit.UTF8_CHARSET);
            List<ThreadState> threads = ThreadDumpParser.parse(text);
            DebuggerUtilsEx.addThreadDump(project, threads, runnerContentUi.getRunnerLayoutUi(), mySearchScope);
            return;
          }
        }
        catch (AttachNotSupportedException e) {
          LOG.debug(e);
        }
        catch (Exception e) {
          LOG.warn(e);
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
      }

      boolean wise = Boolean.getBoolean(ourWiseThreadDumpProperty);
      WiseDumpThreadsListener wiseListener = wise ? new WiseDumpThreadsListener(project, myProcessHandler) : null;

      proxy.sendBreak();

      if (wiseListener != null) {
        wiseListener.after();
      }
    }
  }

  protected static class AttachDebuggerAction extends DumbAwareAction {
    private final AtomicBoolean myEnabled = new AtomicBoolean();
    private final AtomicReference<XDebugSession> myAttachedSession = new AtomicReference<>();
    private final BaseProcessHandler myProcessHandler;
    private MessageBusConnection myConnection = null;

    public AttachDebuggerAction(BaseProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.attach.debugger.action.name"), null, AllIcons.Debugger.AttachToProcess);
      myProcessHandler = processHandler;
      myProcessHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          // 1 second delay to allow jvm to start correctly
          JobScheduler.getScheduler()
            .schedule(() -> myEnabled.set(!myProcessHandler.isProcessTerminating() && !myProcessHandler.isProcessTerminated() &&
                                          JavaDebuggerAttachUtil.canAttach(OSProcessUtil.getProcessID(myProcessHandler.getProcess()))),
                      1, TimeUnit.SECONDS);
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
      JavaDebuggerAttachUtil.attach(OSProcessUtil.getProcessID(myProcessHandler.getProcess()), e.getProject());
    }

    public static void add(RunContentBuilder contentBuilder, ProcessHandler processHandler) {
      if (Registry.is("debugger.attach.to.process.action") && processHandler instanceof BaseProcessHandler) {
        contentBuilder.addAction(new AttachDebuggerAction((BaseProcessHandler)processHandler));
      }
    }
  }

  private static class WiseDumpThreadsListener {
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
          showThreadDump(myListener.getOutput().getStdout(), threadStates, myProject);
        }
      });
    }
  }

  private static void showThreadDump(String out, List<ThreadState> states, Project project) {
    AnalyzeStacktraceUtil.ConsoleFactory factory = states.size() > 1 ? new ThreadDumpConsoleFactory(project, states) : null;
    String title = "Dump " + DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
    ApplicationManager.getApplication().invokeLater(
            () -> AnalyzeStacktraceUtil.addConsole(project, factory, title, out), ModalityState.NON_MODAL);
  }

  protected static class SoftExitAction extends ProxyBasedAction {
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