// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.JavaVersionBasedScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class DebuggerSession implements AbstractDebuggerSession {
  private static final Logger LOG = Logger.getInstance(DebuggerSession.class);
  private final MyDebuggerStateManager myContextManager;

  public enum State {STOPPED, RUNNING, WAITING_ATTACH, PAUSED, WAIT_EVALUATION, DISPOSED}

  public enum Event {ATTACHED, DETACHED, RESUME, STEP, PAUSE, REFRESH, CONTEXT, START_WAIT_ATTACH, DISPOSE, REFRESH_WITH_STACK, THREADS_REFRESH}

  private volatile int myIgnoreFiltersFrameCountThreshold = 0;

  private DebuggerSessionState myState;

  private final String mySessionName;
  private final DebugProcessImpl myDebugProcess;
  private final DebugEnvironment myDebugEnvironment;
  private final GlobalSearchScope myBaseScope;
  private GlobalSearchScope mySearchScope;
  private Sdk myAlternativeJre;
  private final Sdk myRunJre;

  private final DebuggerContextImpl SESSION_EMPTY_CONTEXT;
  /** The thread that the user is currently stepping through. */
  private final AtomicReference<ThreadReferenceProxyImpl> mySteppingThroughThread = new AtomicReference<>();
  private final AtomicReference<ThreadReferenceProxyImpl> myLastThread = new AtomicReference<>();
  private final SingleEdtTaskScheduler updateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();

  private boolean myModifiedClassesScanRequired = false;

  public boolean isSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    return Comparing.equal(mySteppingThroughThread.get(), threadProxy);
  }

  public void setSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    mySteppingThroughThread.set(threadProxy);
  }

  public void clearSteppingThrough() {
    mySteppingThroughThread.set(null);
    resetIgnoreStepFiltersFlag();
  }

  @NotNull
  public GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  public Sdk getAlternativeJre() {
    return myAlternativeJre;
  }

  public void setAlternativeJre(Sdk sdk) {
    myAlternativeJre = sdk;
    updateScope();
    PsiElementFinder.EP.findExtension(AlternativeJreClassFinder.class, getProject()).clearCache();
  }

  public Sdk getRunJre() {
    return myRunJre;
  }

  public DebugEnvironment getDebugEnvironment() {
    return myDebugEnvironment;
  }

  public boolean isModifiedClassesScanRequired() {
    return myModifiedClassesScanRequired;
  }

  public void setModifiedClassesScanRequired(boolean modifiedClassesScanRequired) {
    myModifiedClassesScanRequired = modifiedClassesScanRequired;
  }

  private class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerContextImpl myDebuggerContext;

    MyDebuggerStateManager() {
      myDebuggerContext = SESSION_EMPTY_CONTEXT;
    }

    @NotNull
    @Override
    public DebuggerContextImpl getContext() {
      return myDebuggerContext;
    }

    /**
     * Actually, the state does not change in the same sequence as you call setState.
     * The 'resuming' setState with context.getSuspendContext() == null may be set prior to
     * the setState for the context with context.getSuspendContext().
     * <p>
     * In this case, assume that the latter setState is ignored since the thread was resumed.
     */
    @Override
    public void setState(@NotNull final DebuggerContextImpl context,
                         final State state,
                         final Event event,
                         final @NlsContexts.Label String description) {
      ThreadingAssertions.assertEventDispatchThread();
      final DebuggerSession session = context.getDebuggerSession();
      LOG.assertTrue(session == DebuggerSession.this || session == null);
      final Runnable setStateRunnable = () -> {
        LOG.assertTrue(myDebuggerContext.isInitialised());
        myDebuggerContext = context;
        if (LOG.isDebugEnabled()) {
          LOG.debug("DebuggerSession state = " + state + ", event = " + event);
        }

        myState = new DebuggerSessionState(state, description);
        fireStateChanged(context, event);
      };

      SuspendContextImpl suspendContext = context.getSuspendContext();
      if (suspendContext == null) {
        setStateRunnable.run();
      }
      else {
        suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
          @Override
          public Priority getPriority() {
            return Priority.HIGH;
          }

          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) {
            context.initCaches();
            DebuggerInvocationUtil.swingInvokeLater(getProject(), setStateRunnable);
          }
        });
      }
    }
  }

  static DebuggerSession create(@NotNull final DebugProcessImpl debugProcess, DebugEnvironment environment)
    throws ExecutionException {
    DebuggerSession session = new DebuggerSession(environment.getSessionName(), debugProcess, environment);
    try {
      session.attach();
    }
    catch (ExecutionException e) {
      session.dispose();
      throw e;
    }
    return session;
  }

  private DebuggerSession(@Nls String sessionName, @NotNull final DebugProcessImpl debugProcess, DebugEnvironment environment) {
    mySessionName = sessionName;
    myDebugProcess = debugProcess;
    SESSION_EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext(this, null, null, null);
    myContextManager = new MyDebuggerStateManager();
    myState = new DebuggerSessionState(State.STOPPED, null);
    myDebugProcess.addDebugProcessListener(new MyDebugProcessListener(debugProcess));
    ValueLookupManagerController.getInstance(getProject()).startListening();
    myDebugEnvironment = environment;
    myBaseScope = environment.getSearchScope();
    myAlternativeJre = environment.getAlternativeJre();
    myRunJre = environment.getRunJre();
    updateScope();
  }

  private void updateScope() {
    Sdk jre = myAlternativeJre;
    if (jre == null) jre = myRunJre;
    GlobalSearchScope scope = myBaseScope;
    if (jre != null) {
      LanguageLevel level = LanguageLevel.parse(jre.getVersionString());
      if (level != null) {
        scope = new JavaVersionBasedScope(getProject(), scope, level);
      }
    }
    mySearchScope = scope;
  }

  @NotNull
  public DebuggerStateManager getContextManager() {
    return myContextManager;
  }

  @NotNull
  public Project getProject() {
    return getProcess().getProject();
  }

  public @NlsSafe String getSessionName() {
    return mySessionName;
  }

  @NotNull
  public DebugProcessImpl getProcess() {
    return myDebugProcess;
  }

  private static class DebuggerSessionState {
    final State myState;
    final @NlsContexts.Label String myDescription;

    DebuggerSessionState(State state, @NlsContexts.Label String description) {
      myState = state;
      myDescription = description;
    }
  }

  public State getState() {
    return myState.myState;
  }

  public @NlsContexts.Label String getStateDescription() {
    if (myState.myDescription != null) {
      return myState.myDescription;
    }

    return switch (myState.myState) {
      case STOPPED, DISPOSED -> JavaDebuggerBundle.message("status.debug.stopped");
      case RUNNING -> JavaDebuggerBundle.message("status.app.running");
      case WAITING_ATTACH -> {
        RemoteConnection connection = getProcess().getConnection();
        yield DebuggerUtilsImpl.getConnectionWaitStatus(connection);
      }
      case PAUSED -> JavaDebuggerBundle.message("status.paused");
      case WAIT_EVALUATION -> JavaDebuggerBundle.message("status.waiting.evaluation.result");
    };
  }

  /* Stepping */
  private void resumeAction(final DebugProcessImpl.ResumeCommand command, Event event) {
    myLastThread.set(getContextManager().getContext().getThreadProxy());
    getContextManager().setState(SESSION_EMPTY_CONTEXT, State.WAIT_EVALUATION, event, null);
    myDebugProcess.getManagerThread().schedule(command);
  }

  public void stepOut(int stepSize) {
    SuspendContextImpl suspendContext = getSuspendContext();
    DebugProcessImpl.ResumeCommand cmd =
      DebuggerUtilsImpl.computeSafeIfAny(JvmSteppingCommandProvider.EP_NAME,
                                         handler -> handler.getStepOutCommand(suspendContext, stepSize));
    if (cmd == null) {
      cmd = myDebugProcess.createStepOutCommand(suspendContext, stepSize);
    }
    setSteppingThrough(cmd.getContextThread());
    resumeAction(cmd, Event.STEP);
  }

  public void stepOut() {
    stepOut(StepRequest.STEP_LINE);
  }

  public void stepOver(boolean ignoreBreakpoints, @Nullable MethodFilter methodFilter, int stepSize) {
    SuspendContextImpl suspendContext = getSuspendContext();
    DebugProcessImpl.ResumeCommand cmd =
      DebuggerUtilsImpl.computeSafeIfAny(JvmSteppingCommandProvider.EP_NAME,
                                         handler -> handler.getStepOverCommand(suspendContext, ignoreBreakpoints, stepSize));
    if (cmd == null) {
      cmd = myDebugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, stepSize);
    }
    setSteppingThrough(cmd.getContextThread());
    resumeAction(cmd, Event.STEP);
  }

  public void stepOver(boolean ignoreBreakpoints, int stepSize) {
    stepOver(ignoreBreakpoints, null, stepSize);
  }

  public void stepOver(boolean ignoreBreakpoints) {
    stepOver(ignoreBreakpoints, StepRequest.STEP_LINE);
  }

  public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter, int stepSize) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    DebugProcessImpl.ResumeCommand cmd =
      DebuggerUtilsImpl.computeSafeIfAny(JvmSteppingCommandProvider.EP_NAME,
                                         handler -> handler.getStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize));
    if (cmd == null) {
      cmd = myDebugProcess.createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
    }
    setSteppingThrough(cmd.getContextThread());
    resumeAction(cmd, Event.STEP);
  }

  public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter) {
    stepInto(ignoreFilters, smartStepFilter, StepRequest.STEP_LINE);
  }

  public void stepOverInstruction() {
    SuspendContextImpl suspendContext = getSuspendContext();
    DebugProcessImpl.ResumeCommand cmd = myDebugProcess.new StepOverCommand(suspendContext, false, null, StepRequest.STEP_MIN) {
      @Override
      public @Nullable RequestHint getHint(SuspendContextImpl suspendContext,
                                           ThreadReferenceProxyImpl stepThread,
                                           @Nullable RequestHint parentHint) {
        return null;
      }
    };
    setSteppingThrough(cmd.getContextThread());
    resumeAction(cmd, Event.STEP);
  }

  public void runToCursor(@NotNull XSourcePosition position, final boolean ignoreBreakpoints) {
    try {
      SuspendContextImpl suspendContext = getSuspendContext();
      DebugProcessImpl.ResumeCommand runToCursorCommand =
        DebuggerUtilsImpl.computeSafeIfAny(JvmSteppingCommandProvider.EP_NAME,
                                           handler -> handler.getRunToCursorCommand(suspendContext, position, ignoreBreakpoints));
      if (runToCursorCommand == null) {
        runToCursorCommand = myDebugProcess.createRunToCursorCommand(suspendContext, position, ignoreBreakpoints);
      }

      setSteppingThrough(runToCursorCommand.getContextThread());
      resumeAction(runToCursorCommand, Event.STEP);
    }
    catch (EvaluateException e) {
      Messages.showErrorDialog(e.getMessage(), UIUtil.removeMnemonic(ActionsBundle.actionText(XDebuggerActions.RUN_TO_CURSOR)));
    }
  }


  public void resume() {
    final SuspendContextImpl suspendContext = getSuspendContext();
    if (suspendContext != null) {
      resumeSuspendContext(suspendContext);
    }
  }

  public void resumeSuspendContext(SuspendContextImpl suspendContext) {
    clearSteppingThrough();
    resumeAction(myDebugProcess.createResumeCommand(suspendContext), Event.RESUME);
  }

  public void resetIgnoreStepFiltersFlag() {
    myIgnoreFiltersFrameCountThreshold = 0;
  }

  public void setIgnoreStepFiltersFlag(int currentStackFrameCount) {
    if (myIgnoreFiltersFrameCountThreshold <= 0) {
      myIgnoreFiltersFrameCountThreshold = currentStackFrameCount;
    }
    else {
      myIgnoreFiltersFrameCountThreshold = Math.min(myIgnoreFiltersFrameCountThreshold, currentStackFrameCount);
    }
  }

  public boolean shouldIgnoreSteppingFilters() {
    return myIgnoreFiltersFrameCountThreshold > 0;
  }

  public void pause() {
    myDebugProcess.getManagerThread().schedule(myDebugProcess.createPauseCommand(null));
  }

  /*Presentation*/

  public void showExecutionPoint() {
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(this, getSuspendContext()), State.PAUSED,
                                 Event.REFRESH, null);
  }

  public void refresh(final boolean refreshWithStack) {
    final State state = getState();
    DebuggerContextImpl context = myContextManager.getContext();
    DebuggerContextImpl newContext =
      DebuggerContextImpl.createDebuggerContext(this, context.getSuspendContext(), context.getThreadProxy(), context.getFrameProxy());
    myContextManager.setState(newContext, state, refreshWithStack ? Event.REFRESH_WITH_STACK : Event.REFRESH, null);
  }

  public void dispose() {
    updateAlarm.dispose();
    getProcess().dispose();
    clearSteppingThrough();
    myLastThread.set(null);
    DebuggerInvocationUtil.swingInvokeLater(getProject(), () -> {
      myContextManager.setState(SESSION_EMPTY_CONTEXT, State.DISPOSED, Event.DISPOSE, null);
      myContextManager.dispose();
    });
  }

  // ManagerCommands
  @Override
  public boolean isStopped() {
    return getState() == State.STOPPED;
  }

  public boolean isAttached() {
    return !isStopped() && getState() != State.WAITING_ATTACH;
  }

  @Override
  public boolean isPaused() {
    return getState() == State.PAUSED;
  }

  public boolean isConnecting() {
    return getState() == State.WAITING_ATTACH;
  }

  public boolean isRunning() {
    return getState() == State.RUNNING && !getProcess().getProcessHandler().isProcessTerminated();
  }

  private SuspendContextImpl getSuspendContext() {
    ThreadingAssertions.assertEventDispatchThread();
    return getContextManager().getContext().getSuspendContext();
  }

  private void attach() throws ExecutionException {
    RemoteConnection remoteConnection = myDebugEnvironment.getRemoteConnection();

    myDebugProcess.attachVirtualMachine(myDebugEnvironment, this);

    StringBuilder description = new StringBuilder(JavaDebuggerBundle.message("status.waiting.attach"));
    if (!(remoteConnection instanceof RemoteConnectionStub)) {
      String connectionName = DebuggerUtilsImpl.getConnectionDisplayName(remoteConnection);
      description.append("; ").append(JavaDebuggerBundle.message("status.waiting.attach.address", connectionName));
    }
    DebuggerInvocationUtil.swingInvokeLater(getProject(), () -> {
      getContextManager().setState(SESSION_EMPTY_CONTEXT, State.WAITING_ATTACH, Event.START_WAIT_ATTACH, description.toString());
    });
  }

  private class MyDebugProcessListener extends DebugProcessAdapterImpl {
    private final DebugProcessImpl myDebugProcess;

    MyDebugProcessListener(final DebugProcessImpl debugProcess) {
      myDebugProcess = debugProcess;
    }

    //executed in manager thread
    @Override
    public void connectorIsReady() {
      DebuggerInvocationUtil.invokeLater(getProject(), () -> {
        RemoteConnection connection = myDebugProcess.getConnection();
        String connectionStatusMessage = JavaDebuggerBundle.message(connection.isServerMode() ? "status.listening" : "status.connecting",
                                                                    DebuggerUtilsImpl.getConnectionDisplayName(connection));
        getContextManager().setState(SESSION_EMPTY_CONTEXT, State.WAITING_ATTACH, Event.START_WAIT_ATTACH, connectionStatusMessage);
      });
    }

    @Override
    public void paused(final SuspendContextImpl suspendContext) {
      LOG.debug("paused");

      ThreadReferenceProxyImpl currentThread = suspendContext.getEventThread();

      if (!shouldSetAsActiveContext(suspendContext)) {
        notifyThreadsRefresh();
        ThreadReferenceProxyImpl thread = suspendContext.getEventThread();
        if (thread != null) {
          List<Pair<Breakpoint, com.sun.jdi.event.Event>> descriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
          if (!descriptors.isEmpty()) {
            XDebuggerManagerImpl.getNotificationGroup().createNotification(
                JavaDebuggerBundle.message("status.breakpoint.reached.in.thread", thread.name()),
                JavaDebuggerBundle.message("status.breakpoint.reached.in.thread.switch"),
                NotificationType.INFORMATION)
              .setListener(new BreakpointReachedNotificationListener(suspendContext))
              .notify(getProject());
          }
        }
        if (myDebugProcess.getSuspendManager().getPausedContexts().size() > 1) {
          return;
        }
        else {
          currentThread = mySteppingThroughThread.get();
        }
      }
      else {
        setSteppingThrough(currentThread);
      }

      final StackFrameContext positionContext;
      SourcePosition position;

      if (currentThread == null) {
        //Pause pressed
        LOG.assertTrue(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
        SuspendContextImpl oldContext = getProcess().getSuspendManager().getPausedContext();

        if (oldContext != null) {
          currentThread = oldContext.getThread();
        }

        if (currentThread == null) {
          Collection<ThreadReferenceProxyImpl> allThreads = suspendContext.getVirtualMachineProxy().allThreads();
          ThreadReferenceProxyImpl lastThread = myLastThread.get();
          if (lastThread != null && allThreads.contains(lastThread)) {
            currentThread = lastThread;
          }
          else {
            // heuristics: try to pre-select EventDispatchThread
            currentThread = ContainerUtil.find(allThreads, thread -> ThreadDumper.isEDT(thread.name()));
            if (currentThread == null) {
              // heuristics: try to pre-select the main thread
              currentThread = ContainerUtil.find(allThreads, thread -> "main".equals(thread.name()));
            }
            if (currentThread == null) {
              // heuristics: display the first thread with RUNNABLE status
              for (final ThreadReferenceProxyImpl thread : allThreads) {
                currentThread = thread;
                try {
                  if (currentThread.status() == ThreadReference.THREAD_STATUS_RUNNING && currentThread.frameCount() > 0) {
                    break;
                  }
                }
                catch (EvaluateException ignored) {
                }
              }
            }
          }
        }

        StackFrameProxyImpl proxy = null;
        if (currentThread != null) {
          try {
            while (!currentThread.isSuspended()) {
              // Wait until the thread is considered suspended.
              // Querying data from a thread immediately after VM.suspend() may result in IncompatibleThreadStateException,
              // most likely because some time after suspend() VM erroneously thinks that the thread is still running.
              TimeoutUtil.sleep(10);
            }
            proxy = (currentThread.frameCount() > 0) ? currentThread.frame(0) : null;
          }
          catch (ObjectCollectedException ignored) {
            proxy = null;
          }
          catch (EvaluateException e) {
            proxy = null;
            LOG.error(e);
          }
        }
        positionContext = new SimpleStackFrameContext(proxy, myDebugProcess);
        position = ContextUtil.getSourcePosition(positionContext);
      }
      else {
        positionContext = suspendContext;
        position = myDebugProcess.getPositionManager().getSourcePosition(suspendContext.getLocation());
      }

      if (currentThread != null) {
        try {
          final int frameCount = currentThread.frameCount();
          if (frameCount == 0 || (frameCount <= myIgnoreFiltersFrameCountThreshold)) {
            resetIgnoreStepFiltersFlag();
          }
        }
        catch (EvaluateException e) {
          LOG.info(e);
          resetIgnoreStepFiltersFlag();
        }
      }

      if (position != null) {
        final List<Pair<Breakpoint, com.sun.jdi.event.Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
        final RequestManagerImpl requestsManager = suspendContext.getDebugProcess().getRequestsManager();
        final PsiFile foundFile = position.getFile();
        final boolean sourceMissing = foundFile instanceof PsiCompiledElement;
        for (Pair<Breakpoint, com.sun.jdi.event.Event> eventDescriptor : eventDescriptors) {
          Breakpoint breakpoint = eventDescriptor.getFirst();
          if (breakpoint instanceof LineBreakpoint) {
            final SourcePosition breakpointPosition = ((BreakpointWithHighlighter<?>)breakpoint).getSourcePosition();
            if (breakpointPosition == null || (!sourceMissing && breakpointPosition.getLine() != position.getLine())) {
              requestsManager.deleteRequest(breakpoint);
              requestsManager.setInvalid(breakpoint, JavaDebuggerBundle.message("error.invalid.breakpoint.source.changed"));
              breakpoint.updateUI();
            }
            else if (sourceMissing) {
              // Adjust the position to be the position of the breakpoint in order to show the real originator of the event.
              position = breakpointPosition;
              final StackFrameProxy frameProxy = positionContext.getFrameProxy();
              String className;
              try {
                className = frameProxy != null ? frameProxy.location().declaringType().name() : "";
              }
              catch (EvaluateException ignored) {
                className = "";
              }
              requestsManager.setInvalid(breakpoint, JavaDebuggerBundle.message("error.invalid.breakpoint.source.not.found", className));
              breakpoint.updateUI();
            }
          }
        }
      }

      final DebuggerContextImpl debuggerContext =
        DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, suspendContext, currentThread, null);
      if (suspendContext.getThread() == currentThread) {
        debuggerContext.setPositionCache(position);
      }

      DebuggerInvocationUtil.invokeLater(getProject(), () -> getContextManager().setState(debuggerContext, State.PAUSED, Event.PAUSE,
                                                                                          getDescription(debuggerContext)));
    }

    private boolean shouldSetAsActiveContext(final SuspendContextImpl suspendContext) {
      final ThreadReferenceProxyImpl newThread = suspendContext.getEventThread();
      if (newThread == null || suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL || isSteppingThrough(newThread)) {
        return true;
      }
      final SuspendContextImpl currentSuspendContext = getContextManager().getContext().getSuspendContext();
      if (currentSuspendContext == null) {
        return mySteppingThroughThread.get() == null;
      }
      if (enableBreakpointsDuringEvaluation()) {
        final ThreadReferenceProxyImpl currentThread = currentSuspendContext.getThread();
        return currentThread == null || Comparing.equal(currentThread.getThreadReference(), newThread.getThreadReference());
      }
      return false;
    }


    @Override
    public void resumed(SuspendContextImpl suspendContext) {
      SuspendContextImpl context = getProcess().getSuspendManager().getPausedContext();
      ThreadReferenceProxyImpl steppingThread = null;
      // single thread stepping
      if (context != null
          && suspendContext != null
          && suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD
          && isSteppingThrough(suspendContext.getThread())) {
        steppingThread = suspendContext.getThread();
      }
      final DebuggerContextImpl debuggerContext =
        context != null ?
        DebuggerContextImpl.createDebuggerContext(DebuggerSession.this,
                                                  context,
                                                  steppingThread != null ? steppingThread : context.getThread(),
                                                  null)
                        : null;

      DebuggerInvocationUtil.invokeLater(getProject(), () -> {
        if (debuggerContext != null) {
          getContextManager().setState(debuggerContext, State.PAUSED, Event.CONTEXT, getDescription(debuggerContext));
        }
        else {
          getContextManager().setState(SESSION_EMPTY_CONTEXT, State.RUNNING, Event.CONTEXT, null);
        }
      });
    }

    @Override
    public void processAttached(final DebugProcessImpl process) {
      String message = JavaDebuggerBundle.message("status.connected", DebuggerUtilsImpl.getConnectionDisplayName(process.getConnection()));

      process.printToConsole(message + "\n");
      DebuggerInvocationUtil.invokeLater(getProject(),
                                         () -> getContextManager().setState(SESSION_EMPTY_CONTEXT, State.RUNNING, Event.ATTACHED, message));
    }

    @Override
    public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection) {
      DebuggerInvocationUtil.invokeLater(getProject(), () -> {
        String message = "";
        if (state instanceof RemoteState) {
          message = JavaDebuggerBundle.message("status.connect.failed", DebuggerUtilsImpl.getConnectionDisplayName(remoteConnection));
        }
        message += exception.getMessage();
        getContextManager().setState(SESSION_EMPTY_CONTEXT, State.STOPPED, Event.DETACHED, message);
      });
    }

    @Override
    public void processDetached(final DebugProcessImpl debugProcess, boolean closedByUser) {
      if (!closedByUser) {
        ProcessHandler processHandler = debugProcess.getProcessHandler();
        if (processHandler != null) {
          processHandler.notifyTextAvailable(
            JavaDebuggerBundle.message("status.disconnected", DebuggerUtilsImpl.getConnectionDisplayName(debugProcess.getConnection())) +
            "\n",
            ProcessOutputTypes.SYSTEM);
        }
      }
      DebuggerInvocationUtil.invokeLater(getProject(), () ->
        getContextManager().setState(SESSION_EMPTY_CONTEXT, State.STOPPED, Event.DETACHED,
                                     JavaDebuggerBundle.message("status.disconnected",
                                                                DebuggerUtilsImpl.getConnectionDisplayName(debugProcess.getConnection()))));
      clearSteppingThrough();
    }

    @Override
    public void threadStarted(@NotNull DebugProcess proc, ThreadReference thread) {
      notifyThreadsRefresh();
    }

    @Override
    public void threadStopped(@NotNull DebugProcess proc, ThreadReference thread) {
      notifyThreadsRefresh();
      ThreadReferenceProxyImpl steppingThread = mySteppingThroughThread.get();
      if (steppingThread != null && steppingThread.getThreadReference() == thread) {
        clearSteppingThrough();
      }
      DebugProcessImpl debugProcess = (DebugProcessImpl)proc;
      if (debugProcess.getRequestsManager().getFilterRealThread() == thread) {
        DebuggerManagerEx.getInstanceEx(proc.getProject()).getBreakpointManager().removeThreadFilter(debugProcess);
      }
    }

    private void notifyThreadsRefresh() {
      updateAlarm.cancelAndRequest(ApplicationManager.getApplication().isUnitTestMode() ? 0 : 100, () -> {
        final DebuggerStateManager contextManager = getContextManager();
        contextManager.fireStateChanged(contextManager.getContext(), Event.THREADS_REFRESH);
      });
    }
  }

  private static class BreakpointReachedNotificationListener extends NotificationListener.Adapter {
    private final WeakReference<SuspendContextImpl> myContextRef;

    BreakpointReachedNotificationListener(SuspendContextImpl suspendContext) {
      myContextRef = new WeakReference<>(suspendContext);
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      notification.hideBalloon();
      SuspendContextImpl suspendContext = SoftReference.dereference(myContextRef);
      if (suspendContext != null) {
        switchContext(suspendContext);
      }
    }
  }

  public static void switchContext(@NotNull SuspendContextImpl suspendContext) {
    DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        DebuggerSession session = debugProcess.getSession();
        DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(session, suspendContext, suspendContext.getEventThread(), null);
        DebuggerInvocationUtil.invokeLater(debugProcess.getProject(),
                                           () -> session.getContextManager().setState(debuggerContext, State.PAUSED, Event.PAUSE, null));
      }
    });
  }

  private static String getDescription(DebuggerContextImpl debuggerContext) {
    SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
    if (suspendContext != null && debuggerContext.getThreadProxy() != suspendContext.getThread()) {
      return JavaDebuggerBundle.message("status.paused.in.another.thread");
    }
    return null;
  }

  public static boolean enableBreakpointsDuringEvaluation() {
    return Registry.is("debugger.enable.breakpoints.during.evaluation");
  }

  public static boolean filterBreakpointsDuringSteppingUsingDebuggerEngine() {
    return Registry.is("debugger.filter.breakpoints.during.stepping.using.debugger.engine");
  }

  public void sessionResumed() {
    XDebugSession session = getXDebugSession();
    if (session != null) session.sessionResumed();
  }

  @Nullable
  public XDebugSession getXDebugSession() {
    JavaDebugProcess process = myDebugProcess.getXdebugProcess();
    return process != null ? process.getSession() : null;
  }
}
