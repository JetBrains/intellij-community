// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.sun.jdi.Location;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DebuggerTestCase extends ExecutionWithDebuggerToolsTestCase {
  protected static final int DEFAULT_ADDRESS = 3456;
  protected static final String TEST_JDK_NAME = "JDK";
  protected DebuggerSession myDebuggerSession;
  private ExecutionEnvironment myExecutionEnvironment;
  private RunProfileState myRunnableState;
  private final AtomicInteger myRestart = new AtomicInteger();
  private static final int MAX_RESTARTS = 3;
  private volatile TestDisposable myTestRootDisposable;
  private final List<Runnable> myTearDownRunnables = new ArrayList<>();
  private CompilerManagerImpl myCompilerManager;

  @Override
  protected void tearDown() throws Exception {
    try {
      EdtTestUtil.runInEdtAndWait(() -> FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles());
      if (myDebugProcess != null) {
        myDebugProcess.stop(true);
        myDebugProcess.waitFor();
        myDebugProcess.dispose();
      }
      myTearDownRunnables.forEach(Runnable::run);
      myTearDownRunnables.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
    if (myCompilerManager != null) {
      // after the project disposed ensure there are no Netty threads leaked
      // (we should call this method only after ExternalJavacManager.stop() which happens on project dispose)
      assertTrue(myCompilerManager.awaitNettyThreadPoolTermination(1, TimeUnit.MINUTES));
      myCompilerManager = null;
    }
  }

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    JavaTestUtil.setupInternalJdkAsTestJDK(getTestRootDisposable(), TEST_JDK_NAME);
    DebuggerSettings.getInstance().setTransport(DebuggerSettings.SOCKET_TRANSPORT);
    DebuggerSettings.getInstance().SKIP_CONSTRUCTORS = false;
    DebuggerSettings.getInstance().SKIP_GETTERS      = false;
    NodeRendererSettings.getInstance().getClassRenderer().SHOW_DECLARED_TYPE = true;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    super.runTestRunnable(testRunnable);
    if(getDebugProcess() != null) {
      getDebugProcess().getProcessHandler().startNotify();
      waitProcess(getDebugProcess().getProcessHandler());
      waitForCompleted();
      //disposeSession(myDebuggerSession);
      assertNull(DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(getDebugProcess().getProcessHandler()));
      myDebuggerSession = null;
    }

    // disabled, see JRE-253
    if (false && getChecker().contains("JVMTI_ERROR_WRONG_PHASE(112)")) {
      myRestart.incrementAndGet();
      if (needsRestart()) {
        return;
      }
    } else {
      myRestart.set(0);
    }

    throwExceptionsIfAny();
    checkTestOutput();
  }

  private boolean needsRestart() {
    int restart = myRestart.get();
    return restart > 0 && restart <= MAX_RESTARTS;
  }

  @Override
  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable {
    myTestRootDisposable = new TestDisposable();
    super.runBareRunnable(runnable);
    while (needsRestart()) {
      assert myTestRootDisposable.isDisposed();
      myTestRootDisposable = new TestDisposable();
      super.runBareRunnable(runnable);
    }
  }

  @NotNull
  @Override
  public Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  protected void checkTestOutput() throws Exception {
    getChecker().checkValid(getTestProjectJdk());
  }

  protected void disposeSession(final DebuggerSession debuggerSession) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)debuggerSession::dispose);
  }

  protected void createLocalProcess(String className) throws ExecutionException {
    createLocalProcess(createJavaParameters(className));
  }

  protected void createLocalProcess(JavaParameters javaParameters) throws ExecutionException {
    LOG.assertTrue(myDebugProcess == null);
    myDebuggerSession = createLocalProcess(DebuggerSettings.SOCKET_TRANSPORT, javaParameters);
    myDebugProcess = myDebuggerSession.getProcess();
  }

  protected DebuggerSession createLocalSession(final JavaParameters javaParameters) throws ExecutionException {
    createBreakpoints(javaParameters.getMainClass());
    DebuggerSettings.getInstance().setTransport(DebuggerSettings.SOCKET_TRANSPORT);

    GenericDebuggerRunnerSettings debuggerRunnerSettings = new GenericDebuggerRunnerSettings();
    debuggerRunnerSettings.LOCAL = true;

    RemoteConnection debugParameters = new RemoteConnectionBuilder(
      debuggerRunnerSettings.LOCAL, debuggerRunnerSettings.getTransport(), debuggerRunnerSettings.getDebugPort())
      .project(myProject)
      .asyncAgent(true)
      .create(javaParameters);

    ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(new MockConfiguration(myProject))
      .build();
    myRunnableState = new JavaCommandLineState(environment) {
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @NotNull
      @Override
      protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
        throws ExecutionException {
        return getJavaParameters().toCommandLine(request);
      }
    };

    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        myExecutionEnvironment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
          .runProfile(new MockConfiguration(myProject))
          .build();
        DefaultDebugEnvironment debugEnvironment =
          new DefaultDebugEnvironment(myExecutionEnvironment, myRunnableState, debugParameters, false);
        myDebuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(debugEnvironment);
        XDebuggerManager.getInstance(myProject).startSession(myExecutionEnvironment, new XDebugProcessStarter() {
          @Override
          @NotNull
          public XDebugProcess start(@NotNull XDebugSession session) {
            return JavaDebugProcess.create(session, myDebuggerSession);
          }
        });
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    });
    myDebugProcess = myDebuggerSession.getProcess();

    myDebugProcess.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        print(event.getText(), outputType);
      }
    });

    assertNotNull(myDebuggerSession);
    assertNotNull(myDebugProcess);

    return myDebuggerSession;
  }

  protected int getTraceMode() {
    return VirtualMachine.TRACE_NONE;
  }

  protected DebuggerSession createLocalProcess(int transport, final JavaParameters javaParameters) throws ExecutionException {
    createBreakpoints(javaParameters.getMainClass());

    DebuggerSettings.getInstance().setTransport(transport);

    GenericDebuggerRunnerSettings debuggerRunnerSettings = new GenericDebuggerRunnerSettings();
    debuggerRunnerSettings.setLocal(true);
    debuggerRunnerSettings.setTransport(transport);
    debuggerRunnerSettings.setDebugPort(transport == DebuggerSettings.SOCKET_TRANSPORT ? "0" : String.valueOf(DEFAULT_ADDRESS));

    myExecutionEnvironment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(new MockConfiguration(myProject))
      .build();
    myRunnableState = new JavaCommandLineState(myExecutionEnvironment) {
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @NotNull
      @Override
      protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
        throws ExecutionException {
        return getJavaParameters().toCommandLine(request);
      }
    };

    RemoteConnection debugParameters =
      new RemoteConnectionBuilder(debuggerRunnerSettings.LOCAL,
                                  debuggerRunnerSettings.getTransport(),
                                  debuggerRunnerSettings.getDebugPort())
        .project(myProject)
        .checkValidity(true)
        .asyncAgent(true)
        .create(javaParameters);

    final DebuggerSession[] debuggerSession = {null};
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        ExecutionEnvironment env = myExecutionEnvironment;
        env.putUserData(DefaultDebugEnvironment.DEBUGGER_TRACE_MODE, getTraceMode());
        debuggerSession[0] = attachVirtualMachine(myRunnableState, env, debugParameters, false);
      }
      catch (ExecutionException e) {
        fail(e.getMessage());
      }
    });

    final ProcessHandler processHandler = debuggerSession[0].getProcess().getProcessHandler();
    debuggerSession[0].getProcess().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        print(event.getText(), outputType);
      }
    });

    DebugProcessImpl process =
      (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(processHandler);
    assertNotNull(process);
    return debuggerSession[0];
  }

  protected DebuggerSession createRemoteProcess(final int transport, final boolean serverMode, JavaParameters javaParameters)
          throws ExecutionException {
    RemoteConnection remoteConnection =
      new RemoteConnectionBuilder(serverMode, transport, null)
        .suspend(true)
        .create(javaParameters);

    GeneralCommandLine commandLine = javaParameters.toCommandLine();

    DebuggerSession debuggerSession;

    if (serverMode) {
      debuggerSession = attachVM(remoteConnection, false);
      commandLine.createProcess();
    }
    else {
      commandLine.createProcess();
      debuggerSession = attachVM(remoteConnection, true);
    }

    ProcessHandler processHandler = debuggerSession.getProcess().getProcessHandler();
    DebugProcessImpl process = (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject)
      .getDebugProcess(processHandler);

    assertNotNull(process);
    return debuggerSession;
  }

  protected DebuggerSession attachVM(final RemoteConnection remoteConnection, final boolean pollConnection) {
    final RemoteState remoteState = new RemoteStateState(myProject, remoteConnection);

    final DebuggerSession[] debuggerSession = new DebuggerSession[1];
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        debuggerSession[0] = attachVirtualMachine(remoteState, new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
          .runProfile(new MockConfiguration(myProject))
          .build(), remoteConnection, pollConnection);
      }
      catch (ExecutionException e) {
        fail(e.getMessage());
      }
    });
    debuggerSession[0].getProcess().getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        print(event.getText(), outputType);
      }
    });
    return debuggerSession[0];
  }

  protected void createBreakpoints(final String className) {
    final PsiFile psiFile = ReadAction.compute(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      assertNotNull(psiClass);
      return psiClass.getContainingFile();
    });

    createBreakpoints(psiFile);
  }

  protected Value evaluate(CodeFragmentKind kind, String code, EvaluationContextImpl evaluationContext) throws EvaluateException {
    WatchItemDescriptor watchItemDescriptor = new WatchItemDescriptor(myProject, new TextWithImportsImpl(kind, code));
    watchItemDescriptor.setContext(evaluationContext);
    EvaluateException exception = watchItemDescriptor.getEvaluateException();
    if (exception != null) {
      throw exception;
    }
    return watchItemDescriptor.getValue();
  }

  protected Value evaluate(CodeFragmentKind kind, String code, SuspendContextImpl suspendContext) throws EvaluateException {
    return evaluate(kind, code, createEvaluationContext(suspendContext));
  }

  protected EvaluationContextImpl createEvaluationContext(final SuspendContextImpl suspendContext) {
    try {
      StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
      assertNotNull(proxy);
      return new EvaluationContextImpl(suspendContext, proxy, proxy.thisObject());
    }
    catch (EvaluateException e) {
      error(e);
      return null;
    }
  }

  private void waitForCompleted() {
    final SynchronizationBasedSemaphore s = new SynchronizationBasedSemaphore();
    s.down();

    final InvokeThread.WorkerThreadRequest request = getDebugProcess().getManagerThread().getCurrentRequest();
    final Thread thread = new Thread("Joining "+request) {
      @Override
      public void run() {
        try {
          request.join();
        }
        catch (Exception ignored) {
        }
      }
    };
    thread.start();
    if(request.isDone()) {
      thread.interrupt();
    }
      waitFor(() -> {
        try {
          thread.join();
        }
        catch (InterruptedException ignored) {
        }
      });

    invokeRatherLater(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        LOG.assertTrue(false);
      }

      @Override
      protected void commandCancelled() {
        //We wait for invokeRatherLater's
        invokeRatherLater(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            LOG.assertTrue(false);
          }

          @Override
          protected void commandCancelled() {
            s.up();
          }
        });
      }
    });

    waitFor(s::waitFor);
    myCompilerManager = (CompilerManagerImpl)CompilerManager.getInstance(getProject());
    myCompilerManager.waitForExternalJavacToTerminate(1, TimeUnit.MINUTES);
  }

  private DebuggerContextImpl createDebuggerContext(final SuspendContextImpl suspendContext, StackFrameProxyImpl stackFrame) {
    final DebuggerSession[] session = new DebuggerSession[1];

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> session[0] = DebuggerManagerEx.getInstanceEx(myProject).getSession(suspendContext.getDebugProcess()));

    DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(
            session[0],
            suspendContext,
            stackFrame != null ? stackFrame.threadProxy() : null,
            stackFrame);
    debuggerContext.initCaches();
    return debuggerContext;
  }

  public DebuggerContextImpl createDebuggerContext(final SuspendContextImpl suspendContext) {
    return createDebuggerContext(suspendContext, suspendContext.getFrameProxy());
  }

  protected void printLocation(SuspendContextImpl suspendContext) {
    try {
      Location location = suspendContext.getFrameProxy().location();
      String message = "paused at " + location.sourceName() + ":" + location.lineNumber();
      println(message, ProcessOutputTypes.SYSTEM);
    }
    catch (Throwable e) {
      addException(e);
    }
  }

  protected void createBreakpointInHelloWorld() {
    DebuggerInvocationUtil.invokeAndWait(myProject, () -> {
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass("HelloWorld", GlobalSearchScope.allScope(myProject));
      assertNotNull(psiClass);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
      breakpointManager.addLineBreakpoint(document, 3);
    }, ApplicationManager.getApplication().getDefaultModalityState());
  }

  protected void createHelloWorldProcessWithBreakpoint() throws ExecutionException {
    createLocalProcess("HelloWorld");

    createBreakpointInHelloWorld();
  }

  @Override
  protected DebugProcessImpl getDebugProcess() {
    return myDebuggerSession != null ? myDebuggerSession.getProcess() : null;
  }

  public DebuggerSession getDebuggerSession() {
    return myDebuggerSession;
  }

  public ExecutionEnvironment getExecutionEnvironment() {
    return myExecutionEnvironment;
  }

  public RunProfileState getRunnableState() {
    return myRunnableState;
  }

  protected DebuggerSession attachVirtualMachine(RunProfileState state,
                                                 ExecutionEnvironment environment,
                                                 RemoteConnection remoteConnection,
                                                 boolean pollConnection) throws ExecutionException {
    final DebuggerSession debuggerSession =
      DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(new DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection));
    XDebuggerManager.getInstance(myProject).startSession(environment, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull XDebugSession session) {
        return JavaDebugProcess.create(session, debuggerSession);
      }
    });
    return debuggerSession;
  }

  public class MockConfiguration implements ModuleRunConfiguration {
    private final Project project;

    public MockConfiguration(Project project) {
      this.project = project;
    }

    @Override
    public Module @NotNull [] getModules() {
      return myModule == null ? Module.EMPTY_ARRAY : new Module[]{myModule};
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public ConfigurationFactory getFactory() {
      return UnknownConfigurationType.getInstance();
    }

    @Override
    public void setName(@NotNull String name) { }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Project getProject() {
      return project;
    }

    @Override
    public RunConfiguration clone() {
      return null;
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
      return null;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }
  }

  protected void disableRenderer(NodeRenderer renderer) {
    setRendererEnabled(renderer, false);
  }

  protected void enableRenderer(NodeRenderer renderer) {
    setRendererEnabled(renderer, true);
  }

  private void setRendererEnabled(NodeRenderer renderer, boolean state) {
    boolean oldValue = renderer.isEnabled();
    if (oldValue != state) {
      myTearDownRunnables.add(() -> renderer.setEnabled(oldValue));
      renderer.setEnabled(state);
    }
  }

  protected void doWhenXSessionPausedThenResume(ThrowableRunnable runnable) {
    XDebugSession session = getDebuggerSession().getXDebugSession();
    assertNotNull(session);
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            runnable.run();
          }
          catch (Throwable e) {
            addException(e);
          }
          finally {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(session::resume);
          }
        });
      }
    });
  }
}
