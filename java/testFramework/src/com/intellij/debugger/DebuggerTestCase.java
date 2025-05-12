// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class DebuggerTestCase extends ExecutionWithDebuggerToolsTestCase {
  protected static final int DEFAULT_ADDRESS = 3456;
  protected static final String TEST_JDK_NAME = "JDK";
  protected DebuggerSession myDebuggerSession;
  private ExecutionEnvironment myExecutionEnvironment;
  private RunProfileState myRunnableState;
  private final List<ThrowableRunnable<Throwable>> myTearDownRunnables = new ArrayList<>();
  private CompilerManagerImpl myCompilerManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    atDebuggerTearDown(() -> {
      if (myDebugProcess != null) {
        myDebugProcess.stop(true);
        myDebugProcess.waitFor();
        myDebugProcess.dispose();
      }
    });
    atDebuggerTearDown(() -> {
      EdtTestUtil.runInEdtAndWait(() -> {
        FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
      });
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      new RunAll(myTearDownRunnables).run();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTearDownRunnables.clear();
      super.tearDown();
    }
    if (myCompilerManager != null) {
      // after the project disposed ensure there are no Netty threads leaked
      // (we should call this method only after ExternalJavacManager.stop() which happens on project dispose)
      assertTrue(myCompilerManager.awaitNettyThreadPoolTermination(1, TimeUnit.MINUTES));
      myCompilerManager = null;
    }
  }

  /**
   * Run the given runnable as part of {@link DebuggerTestCase#tearDown() DebuggerTestCase.tearDown()}.
   * The runnables are run in reverse order of registration.
   * <p>
   * See {@link #getTestRootDisposable() getTestRootDisposable()} to run some code a bit later,
   * as part of {@link UsefulTestCase#tearDown()}.
   */
  protected final void atDebuggerTearDown(ThrowableRunnable<Throwable> runnable) {
    myTearDownRunnables.add(0, runnable);
  }

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    JavaTestUtil.setupInternalJdkAsTestJDK(getTestRootDisposable(), TEST_JDK_NAME);
    DebuggerSettings.getInstance().setTransport(DebuggerSettings.SOCKET_TRANSPORT);
    DebuggerSettings.getInstance().SKIP_CONSTRUCTORS = false;
    DebuggerSettings.getInstance().SKIP_GETTERS = false;
    NodeRendererSettings.getInstance().getClassRenderer().SHOW_DECLARED_TYPE = true;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    super.runTestRunnable(testRunnable);
    if (getDebugProcess() != null) {
      getDebugProcess().getProcessHandler().startNotify();
      waitProcess(getDebugProcess().getProcessHandler());
      waitForCompleted();
      //disposeSession(myDebuggerSession);
      assertNull(DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(getDebugProcess().getProcessHandler()));
      myDebuggerSession = null;
    }

    throwExceptionsIfAny();
    checkTestOutput();
  }

  /**
   * Ensures that the actual output from {@link #systemPrintln(String)} and the related methods
   * matches the expected output from the {@code .out} file.
   * <p>
   * To disable this check, override this method.
   */
  protected void checkTestOutput() throws Exception {
    getChecker().checkValid(getTestProjectJdk());
  }

  protected void disposeSession(final DebuggerSession debuggerSession) {
    UIUtil.invokeAndWaitIfNeeded(debuggerSession::dispose);
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
      .runProfile(new MockConfiguration(myProject, myModule))
      .build();
    myRunnableState = new JavaCommandLineState(environment) {
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @Override
      protected @NotNull TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
        throws ExecutionException {
        return getJavaParameters().toCommandLine(request);
      }
    };

    myExecutionEnvironment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runProfile(new MockConfiguration(myProject, myModule))
      .build();
    DefaultDebugEnvironment debugEnvironment =
      new DefaultDebugEnvironment(myExecutionEnvironment, myRunnableState, debugParameters, false);
    myDebuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(debugEnvironment);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        XDebugSessionImpl session =
          (XDebugSessionImpl)XDebuggerManager.getInstance(myProject).startSession(myExecutionEnvironment, new XDebugProcessStarter() {
          @Override
          public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
            return JavaDebugProcess.create(session, myDebuggerSession);
          }
        });
        session.activateSession(false); // activate the session immediately
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }, ModalityState.any());
    myDebugProcess = myDebuggerSession.getProcess();

    myDebugProcess.addProcessListener(new ProcessListener() {
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
      .runProfile(new MockConfiguration(myProject, myModule))
      .build();
    myRunnableState = new JavaCommandLineState(myExecutionEnvironment) {
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @Override
      protected @NotNull TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
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
        .asyncAgent(false) // add manually to allow early tmp folder deletion
        .create(javaParameters);

    AsyncStacksUtils.addDebuggerAgent(javaParameters, myProject, true, getTestRootDisposable());

    myExecutionEnvironment.putUserData(DefaultDebugEnvironment.DEBUGGER_TRACE_MODE, getTraceMode());
    DebuggerSession debuggerSession = attachVirtualMachine(myRunnableState, myExecutionEnvironment, debugParameters, false);

    final ProcessHandler processHandler = debuggerSession.getProcess().getProcessHandler();
    debuggerSession.getProcess().addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        print(event.getText(), outputType);
      }
    });

    DebugProcessImpl process =
      (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(processHandler);
    assertNotNull(process);
    return debuggerSession;
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
    RemoteState remoteState = new RemoteStateState(myProject, remoteConnection);
    ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runProfile(new MockConfiguration(myProject, myModule))
      .build();
    DebuggerSession debuggerSession = null;
    try {
      debuggerSession = attachVirtualMachine(remoteState, environment, remoteConnection, pollConnection);
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
    debuggerSession.getProcess().getProcessHandler().addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        print(event.getText(), outputType);
      }
    });
    return debuggerSession;
  }

  protected void createBreakpoints(final String className) {
    final PsiFile psiFile = ReadAction.compute(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      assertNotNull(className, psiClass);
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
    final Thread thread = new Thread("Joining " + request) {
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
    if (request.isDone()) {
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

    UIUtil.invokeAndWaitIfNeeded(() -> {
      session[0] = DebuggerManagerEx.getInstanceEx(myProject).getSession(suspendContext.getDebugProcess());
    });

    DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(
      session[0],
      suspendContext,
      stackFrame != null ? stackFrame.threadProxy() : null,
      stackFrame);
    debuggerContext.initCaches();
    return debuggerContext;
  }

  public DebuggerContextImpl createDebuggerContext(final SuspendContextImpl suspendContext) {
    StackFrameProxyImpl proxy = getFrameProxy(suspendContext);
    return createDebuggerContext(suspendContext, proxy);
  }

  protected static StackFrameProxyImpl getFrameProxy(@NotNull SuspendContextImpl suspendContext) {
    return suspendContext.getFrameProxy();
  }

  protected void createBreakpointInHelloWorld() {
    DebuggerInvocationUtil.invokeAndWait(myProject, () -> {
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass("HelloWorld", GlobalSearchScope.allScope(myProject));
      assertNotNull(psiClass);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
      assertNotNull(breakpointManager.addLineBreakpoint(document, 3));
    }, ApplicationManager.getApplication().getDefaultModalityState());
  }

  protected void createHelloWorldProcessWithBreakpoint() throws ExecutionException {
    createLocalProcess("HelloWorld");

    createBreakpointInHelloWorld();
  }

  protected void printAsyncStackTrace(boolean showLineNumbers) {
    if (!myLogAllCommands) {
      printContext(getDebugProcess().getDebuggerContext());
    }
    List<XStackFrame> frames = collectFrames(getDebuggerSession().getXDebugSession());
    systemPrintln("vvv stack trace vvv");
    frames.forEach(f -> {
      if (f instanceof XDebuggerFramesList.ItemWithSeparatorAbove withSeparator && withSeparator.hasSeparatorAbove()) {
        systemPrintln("-- " + withSeparator.getCaptionAboveOf() + " --");
      }
      if (f instanceof XFramesView.HiddenStackFramesItem) {
        systemPrintln("  <hidden frames>");
      }
      else {
        systemPrintln("  " + frameRepresentation(f, showLineNumbers));
      }
    });
    systemPrintln("^^^ stack trace ^^^");
  }

  private String frameRepresentation(XStackFrame f, boolean showLineNumbers) {
    return showLineNumbers ? StringUtil.substringBeforeLast(getFramePresentation(f), "(") :
      StringUtil.substringBeforeLast(getFramePresentation(f), ":");
  }

  protected @NotNull List<XStackFrame> collectFrames(@Nullable XDebugSession session) {
    return List.of();
  }

  protected @NotNull String getFramePresentation(XStackFrame f) {
    return f.toString();
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

  public DebuggerSession attachVirtualMachine(RunProfileState state,
                                                 ExecutionEnvironment environment,
                                                 RemoteConnection remoteConnection,
                                                 boolean pollConnection) throws ExecutionException {
    assertFalse(EDT.isCurrentThreadEdt());
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject)
      .attachVirtualMachine(new DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection));
    assertNotNull(debuggerSession);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        XDebuggerManager.getInstance(myProject).startSession(environment, new XDebugProcessStarter() {
          @Override
          public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
            return JavaDebugProcess.create(session, debuggerSession);
          }
        });
      }
      catch (ExecutionException e) {
        fail(e.getMessage());
      }
    }, ModalityState.any());
    return debuggerSession;
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
      atDebuggerTearDown(() -> renderer.setEnabled(oldValue));
      renderer.setEnabled(state);
    }
  }
  protected void doWhenXSessionPaused(ThrowableRunnable runnable) {
    doWhenXSessionPaused(runnable, false);
  }

  protected void doWhenXSessionPausedThenResume(ThrowableRunnable runnable) {
    doWhenXSessionPaused(runnable, true);
  }

  private void doWhenXSessionPaused(ThrowableRunnable runnable, boolean thenResume) {
    XDebugSession session = getDebuggerSession().getXDebugSession();
    assertNotNull(session);
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (myLogAllCommands) {
          printContext("Stopped at ", getDebugProcess().getDebuggerContext());
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            runnable.run();
          }
          catch (Throwable e) {
            addException(e);
          }
          finally {
            if (thenResume) {
              resume();
            }
          }
        });
      }
    });
  }

  protected void resume() {
    SwingUtilities.invokeLater(() -> {
      if (myLogAllCommands) {
        printContext("Resuming ", getDebugProcess().getDebuggerContext());
      }
      XDebugSession session = getDebuggerSession().getXDebugSession();
      assertNotNull(session);
      session.resume();
    });
  }

  protected void setUpPacketsMeasureTest() {
    ApplicationManagerEx.setInStressTest(true);
    setRegistryPropertyForTest("debugger.track.instrumentation", "false");
    setRegistryPropertyForTest("debugger.evaluate.single.threaded.timeout", "-1");
    setRegistryPropertyForTest("debugger.preload.types.hierarchy", "false");

    boolean dfa = ViewsGeneralSettings.getInstance().USE_DFA_ASSIST;
    boolean dfaGray = ViewsGeneralSettings.getInstance().USE_DFA_ASSIST_GRAY_OUT;
    ViewsGeneralSettings.getInstance().USE_DFA_ASSIST = false;
    ViewsGeneralSettings.getInstance().USE_DFA_ASSIST_GRAY_OUT = false;
    Disposer.register(getTestRootDisposable(), () -> {
      ViewsGeneralSettings.getInstance().USE_DFA_ASSIST = dfa;
      ViewsGeneralSettings.getInstance().USE_DFA_ASSIST_GRAY_OUT = dfaGray;
    });
  }
}
