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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationListener;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DebuggerSession implements AbstractDebuggerSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerSession");
  // flags
  private final MyDebuggerStateManager myContextManager;

  public static final int STATE_STOPPED = 0;
  public static final int STATE_RUNNING = 1;
  public static final int STATE_WAITING_ATTACH = 2;
  public static final int STATE_PAUSED = 3;
  public static final int STATE_WAIT_EVALUATION = 5;
  public static final int STATE_DISPOSED = 6;

  public static final int EVENT_ATTACHED = 0;
  public static final int EVENT_DETACHED = 1;
  public static final int EVENT_RESUME = 4;
  public static final int EVENT_STEP = 5;
  public static final int EVENT_PAUSE = 6;
  public static final int EVENT_REFRESH = 7;
  public static final int EVENT_CONTEXT = 8;
  public static final int EVENT_START_WAIT_ATTACH = 9;
  public static final int EVENT_DISPOSE = 10;
  public static final int EVENT_REFRESH_VIEWS_ONLY = 11;
  public static final int EVENT_THREADS_REFRESH = 12;

  private volatile boolean myIsEvaluating;
  private volatile int myIgnoreFiltersFrameCountThreshold = 0;

  private DebuggerSessionState myState = null;

  private final String mySessionName;
  private final DebugProcessImpl myDebugProcess;
  private @NotNull GlobalSearchScope mySearchScope;

  private final DebuggerContextImpl SESSION_EMPTY_CONTEXT;
  //Thread, user is currently stepping through
  private final Set<ThreadReferenceProxyImpl> mySteppingThroughThreads = ContainerUtil.newConcurrentSet();
  protected final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myModifiedClassesScanRequired = false;

  public boolean isSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    return mySteppingThroughThreads.contains(threadProxy);
  }

  public boolean setSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    return mySteppingThroughThreads.add(threadProxy);
  }

  @NotNull
  public GlobalSearchScope getSearchScope() {
    //noinspection ConstantConditions
    LOG.assertTrue(mySearchScope != null, "Accessing Session's search scope before its initialization");
    return mySearchScope;
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

    @Override
    public DebuggerContextImpl getContext() {
      return myDebuggerContext;
    }

    /**
     * actually state changes not in the same sequence as you call setState
     * the 'resuming' setState with context.getSuspendContext() == null may be set prior to
     * the setState for the context with context.getSuspendContext()
     *
     * in this case we assume that the latter setState is ignored
     * since the thread was resumed
     */
    @Override
    public void setState(final DebuggerContextImpl context, final int state, final int event, final String description) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      final DebuggerSession session = context.getDebuggerSession();
      LOG.assertTrue(session == DebuggerSession.this || session == null);
      final Runnable setStateRunnable = new Runnable() {
        @Override
        public void run() {
          LOG.assertTrue(myDebuggerContext.isInitialised());
          myDebuggerContext = context;
          if (LOG.isDebugEnabled()) {
            LOG.debug("DebuggerSession state = " + state + ", event = " + event);
          }

          myIsEvaluating = false;

          myState = new DebuggerSessionState(state, description);
          fireStateChanged(context, event);
        }
      };

      if(context.getSuspendContext() == null) {
        setStateRunnable.run();
      }
      else {
        getProcess().getManagerThread().schedule(new SuspendContextCommandImpl(context.getSuspendContext()) {
          @Override
          public void contextAction() throws Exception {
            context.initCaches();
            DebuggerInvocationUtil.swingInvokeLater(getProject(), setStateRunnable);
          }
        });
      }
    }
  }

  protected DebuggerSession(String sessionName, final DebugProcessImpl debugProcess) {
    mySessionName  = sessionName;
    myDebugProcess = debugProcess;
    SESSION_EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext(this, null, null, null);
    myContextManager = new MyDebuggerStateManager();
    myState = new DebuggerSessionState(STATE_STOPPED, null);
    myDebugProcess.addDebugProcessListener(new MyDebugProcessListener(debugProcess));
    myDebugProcess.addEvaluationListener(new MyEvaluationListener());
    ValueLookupManager.getInstance(getProject()).startListening();
  }

  @NotNull
  public DebuggerStateManager getContextManager() {
    return myContextManager;
  }

  public Project getProject() {
    return getProcess().getProject();
  }

  public String getSessionName() {
    return mySessionName;
  }

  public DebugProcessImpl getProcess() {
    return myDebugProcess;
  }

  private static class DebuggerSessionState {
    final int myState;
    final String myDescription;

    public DebuggerSessionState(int state, String description) {
      myState = state;
      myDescription = description;
    }
  }

  public int getState() {
    return myState.myState;
  }

  public String getStateDescription() {
    if (myState.myDescription != null) {
      return myState.myDescription;
    }

    switch (myState.myState) {
      case STATE_STOPPED:
        return DebuggerBundle.message("status.debug.stopped");
      case STATE_RUNNING:
        return DebuggerBundle.message("status.app.running");
      case STATE_WAITING_ATTACH:
        RemoteConnection connection = getProcess().getConnection();
        final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
        final String transportName = DebuggerBundle.getTransportName(connection);
        return connection.isServerMode() ? DebuggerBundle.message("status.listening", addressDisplayName, transportName) : DebuggerBundle.message("status.connecting", addressDisplayName, transportName);
      case STATE_PAUSED:
        return DebuggerBundle.message("status.paused");
      case STATE_WAIT_EVALUATION:
        return DebuggerBundle.message("status.waiting.evaluation.result");
      case STATE_DISPOSED:
        return DebuggerBundle.message("status.debug.stopped");
    }
    return null;
  }

  /* Stepping */
  private void resumeAction(final DebugProcessImpl.ResumeCommand command, int event) {
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAIT_EVALUATION, event, null);
    myDebugProcess.getManagerThread().schedule(command);
  }

  public void stepOut(int stepSize) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    final DebugProcessImpl.ResumeCommand cmd = myDebugProcess.createStepOutCommand(suspendContext, stepSize);
    mySteppingThroughThreads.add(cmd.getContextThread());
    resumeAction(cmd, EVENT_STEP);
  }

  public void stepOut() {
    stepOut(StepRequest.STEP_LINE);
  }

  public void stepOver(boolean ignoreBreakpoints, int stepSize) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    final DebugProcessImpl.ResumeCommand cmd = myDebugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints, stepSize);
    mySteppingThroughThreads.add(cmd.getContextThread());
    resumeAction(cmd, EVENT_STEP);
  }

  public void stepOver(boolean ignoreBreakpoints) {
    stepOver(ignoreBreakpoints, StepRequest.STEP_LINE);
  }

  public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter, int stepSize) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    final DebugProcessImpl.ResumeCommand cmd = myDebugProcess.createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
    mySteppingThroughThreads.add(cmd.getContextThread());
    resumeAction(cmd, EVENT_STEP);
  }

  public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter) {
    stepInto(ignoreFilters, smartStepFilter, StepRequest.STEP_LINE);
  }

  public void runToCursor(@NotNull XSourcePosition position, final boolean ignoreBreakpoints) {
    try {
      DebugProcessImpl.ResumeCommand runToCursorCommand = myDebugProcess.createRunToCursorCommand(getSuspendContext(), position, ignoreBreakpoints);
      mySteppingThroughThreads.add(runToCursorCommand.getContextThread());
      resumeAction(runToCursorCommand, EVENT_STEP);
    }
    catch (EvaluateException e) {
      Messages.showErrorDialog(e.getMessage(), UIUtil.removeMnemonic(ActionsBundle.actionText(XDebuggerActions.RUN_TO_CURSOR)));
    }
  }


  public void resume() {
    final SuspendContextImpl suspendContext = getSuspendContext();
    if(suspendContext != null) {
      if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
        mySteppingThroughThreads.clear();
      }
      else {
        mySteppingThroughThreads.remove(suspendContext.getThread());
      }
      resetIgnoreStepFiltersFlag();
      resumeAction(myDebugProcess.createResumeCommand(suspendContext), EVENT_RESUME);
    }
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
    myDebugProcess.getManagerThread().schedule(myDebugProcess.createPauseCommand());
  }

  /*Presentation*/

  public void showExecutionPoint() {
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(this, getSuspendContext()), STATE_PAUSED, EVENT_REFRESH, null);
  }

  public void refresh(final boolean refreshViewsOnly) {
    final int state = getState();
    DebuggerContextImpl context = myContextManager.getContext();
    DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(this, context.getSuspendContext(), context.getThreadProxy(), context.getFrameProxy());
    myContextManager.setState(newContext, state, refreshViewsOnly? EVENT_REFRESH_VIEWS_ONLY : EVENT_REFRESH, null);
  }

  public void dispose() {
    getProcess().dispose();
    Disposer.dispose(myUpdateAlarm);
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      @Override
      public void run() {
        getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_DISPOSED, EVENT_DISPOSE, null);
      }
    });
  }

  // ManagerCommands
  @Override
  public boolean isStopped() {
    return getState() == STATE_STOPPED;
  }

  public boolean isAttached() {
    return !isStopped() && getState() != STATE_WAITING_ATTACH;
  }

  @Override
  public boolean isPaused() {
    return getState() == STATE_PAUSED;
  }

  public boolean isConnecting() {
    return getState() == STATE_WAITING_ATTACH;
  }

  public boolean isEvaluating() {
    return myIsEvaluating;
  }

  public boolean isRunning() {
    return getState() == STATE_RUNNING && !getProcess().getProcessHandler().isProcessTerminated();
  }

  private SuspendContextImpl getSuspendContext() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getContextManager().getContext().getSuspendContext();
  }

  @Nullable
  protected ExecutionResult attach(DebugEnvironment environment) throws ExecutionException {
    RemoteConnection remoteConnection = environment.getRemoteConnection();
    final String addressDisplayName = DebuggerBundle.getAddressDisplayName(remoteConnection);
    final String transportName = DebuggerBundle.getTransportName(remoteConnection);
    mySearchScope = environment.getSearchScope();
    final ExecutionResult executionResult = myDebugProcess.attachVirtualMachine(environment, this);
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAITING_ATTACH, EVENT_START_WAIT_ATTACH, DebuggerBundle.message("status.waiting.attach", addressDisplayName, transportName));
    return executionResult;
  }

  private class MyDebugProcessListener extends DebugProcessAdapterImpl {
    private final DebugProcessImpl myDebugProcess;

    public MyDebugProcessListener(final DebugProcessImpl debugProcess) {
      myDebugProcess = debugProcess;
    }

    //executed in manager thread
    @Override
    public void connectorIsReady() {
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          RemoteConnection connection = myDebugProcess.getConnection();
          final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
          final String transportName = DebuggerBundle.getTransportName(connection);
          final String connectionStatusMessage = connection.isServerMode() ? DebuggerBundle.message("status.listening", addressDisplayName, transportName) : DebuggerBundle.message("status.connecting", addressDisplayName, transportName);
          getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAITING_ATTACH, EVENT_START_WAIT_ATTACH, connectionStatusMessage);
        }
      });
    }

    @Override
    public void paused(final SuspendContextImpl suspendContext) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("paused");
      }

      if (!shouldSetAsActiveContext(suspendContext)) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          @Override
          public void run() {
            getContextManager().fireStateChanged(getContextManager().getContext(), EVENT_THREADS_REFRESH);
          }
        });
        return;
      }

      ThreadReferenceProxyImpl currentThread   = suspendContext.getThread();
      final StackFrameContext positionContext;

      if (currentThread == null) {
        //Pause pressed
        LOG.assertTrue(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
        SuspendContextImpl oldContext = getProcess().getSuspendManager().getPausedContext();

        if (oldContext != null) {
          currentThread = oldContext.getThread();
        }

        if(currentThread == null) {
          final Collection<ThreadReferenceProxyImpl> allThreads = getProcess().getVirtualMachineProxy().allThreads();
          // heuristics: try to pre-select EventDispatchThread
          for (final ThreadReferenceProxyImpl thread : allThreads) {
            if (ThreadState.isEDT(thread.name())) {
              currentThread = thread;
              break;
            }
          }
          if (currentThread == null) {
            // heuristics: display the first thread with RUNNABLE status
            for (final ThreadReferenceProxyImpl thread : allThreads) {
              currentThread = thread;
              if (currentThread.status() == ThreadReference.THREAD_STATUS_RUNNING) {
                break;
              }
            }
          }
        }

        StackFrameProxyImpl proxy = null;
        if (currentThread != null) {
          try {
            while (!currentThread.isSuspended()) {
              // wait until thread is considered suspended. Querying data from a thread immediately after VM.suspend()
              // may result in IncompatibleThreadStateException, most likely some time after suspend() VM erroneously thinks that thread is still running
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
      }
      else {
        positionContext = suspendContext;
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

      SourcePosition position = PsiDocumentManager.getInstance(getProject()).commitAndRunReadAction(new Computable<SourcePosition>() {
        @Override
        public @Nullable SourcePosition compute() {
          return ContextUtil.getSourcePosition(positionContext);
        }
      });

      if (position != null) {
        final List<Pair<Breakpoint, Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
        final RequestManagerImpl requestsManager = suspendContext.getDebugProcess().getRequestsManager();
        final PsiFile foundFile = position.getFile();
        final boolean sourceMissing = foundFile instanceof PsiCompiledElement;
        for (Pair<Breakpoint, Event> eventDescriptor : eventDescriptors) {
          Breakpoint breakpoint = eventDescriptor.getFirst();
          if (breakpoint instanceof LineBreakpoint) {
            final SourcePosition breakpointPosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
            if (breakpointPosition == null || (!sourceMissing && breakpointPosition.getLine() != position.getLine())) {
              requestsManager.deleteRequest(breakpoint);
              requestsManager.setInvalid(breakpoint, DebuggerBundle.message("error.invalid.breakpoint.source.changed"));
              breakpoint.updateUI();
            }
            else if (sourceMissing) {
              // adjust position to be position of the breakpoint in order to show the real originator of the event
              position = breakpointPosition;
              final StackFrameProxy frameProxy = positionContext.getFrameProxy();
              String className;
              try {
                className = frameProxy != null? frameProxy.location().declaringType().name() : "";
              }
              catch (EvaluateException ignored) {
                className = "";
              }
              requestsManager.setInvalid(breakpoint, DebuggerBundle.message("error.invalid.breakpoint.source.not.found", className));
              breakpoint.updateUI();
            }
          }
        }
      }

      final DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, suspendContext, currentThread, null);
      debuggerContext.setPositionCache(position);

      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          getContextManager().setState(debuggerContext, STATE_PAUSED, EVENT_PAUSE, null);
        }
      });
    }

    private boolean shouldSetAsActiveContext(final SuspendContextImpl suspendContext) {
      final ThreadReferenceProxyImpl newThread = suspendContext.getThread();
      if (newThread == null || suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL || isSteppingThrough(newThread)) {
        return true;
      }
      final SuspendContextImpl currentSuspendContext = getContextManager().getContext().getSuspendContext();
      if (currentSuspendContext == null) {
        return true;
      }
      if (enableBreakpointsDuringEvaluation()) {
        final ThreadReferenceProxyImpl currentThread = currentSuspendContext.getThread();
        return currentThread == null || Comparing.equal(currentThread.getThreadReference(), newThread.getThreadReference());
      }
      return false;
    }


    @Override
    public void resumed(final SuspendContextImpl suspendContext) {
      final SuspendContextImpl currentContext = isSteppingThrough(suspendContext.getThread()) ? null : getProcess().getSuspendManager().getPausedContext();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          if (currentContext != null) {
            getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, currentContext), STATE_PAUSED, EVENT_CONTEXT, null);
          }
          else {
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_CONTEXT, null);
          }
        }
      });
    }

    @Override
    public void processAttached(final DebugProcessImpl process) {
      final RemoteConnection connection = getProcess().getConnection();
      final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
      final String transportName = DebuggerBundle.getTransportName(connection);
      final String message = DebuggerBundle.message("status.connected", addressDisplayName, transportName);

      process.printToConsole(message + "\n");
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_ATTACHED, message);
        }
      });
    }

    @Override
    public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection) {
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          String message = "";
          if (state instanceof RemoteState) {
            message = DebuggerBundle.message("status.connect.failed", DebuggerBundle.getAddressDisplayName(remoteConnection), DebuggerBundle.getTransportName(remoteConnection));
          }
          message += exception.getMessage();
          getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_STOPPED, EVENT_DETACHED, message);
        }
      });
    }

    @Override
    public void processDetached(final DebugProcessImpl debugProcess, boolean closedByUser) {
      if (!closedByUser) {
        ProcessHandler processHandler = debugProcess.getProcessHandler();
        if(processHandler != null) {
          final RemoteConnection connection = getProcess().getConnection();
          final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
          final String transportName = DebuggerBundle.getTransportName(connection);
          processHandler.notifyTextAvailable(DebuggerBundle.message("status.disconnected", addressDisplayName, transportName) + "\n", ProcessOutputTypes.SYSTEM);
        }
      }
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          final RemoteConnection connection = getProcess().getConnection();
          final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
          final String transportName = DebuggerBundle.getTransportName(connection);
          getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_STOPPED, EVENT_DETACHED, DebuggerBundle.message("status.disconnected", addressDisplayName, transportName));
        }
      });
      mySteppingThroughThreads.clear();
    }

    @Override
    public void threadStarted(DebugProcess proc, ThreadReference thread) {
      notifyThreadsRefresh();
    }

    @Override
    public void threadStopped(DebugProcess proc, ThreadReference thread) {
      notifyThreadsRefresh();
    }

    private void notifyThreadsRefresh() {
      if (!myUpdateAlarm.isDisposed()) {
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            final DebuggerStateManager contextManager = getContextManager();
            contextManager.fireStateChanged(contextManager.getContext(), EVENT_THREADS_REFRESH);
          }
        }, 100, ModalityState.NON_MODAL);
      }
    }
  }

  private class MyEvaluationListener implements EvaluationListener {
    @Override
    public void evaluationStarted(SuspendContextImpl context) {
      myIsEvaluating = true;
    }

    @Override
    public void evaluationFinished(final SuspendContextImpl context) {
      myIsEvaluating = false;
      // seems to be not required after move to xdebugger
      //DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      //  @Override
      //  public void run() {
      //    if (context != getSuspendContext()) {
      //      getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, context), STATE_PAUSED, EVENT_REFRESH, null);
      //    }
      //  }
      //});
    }
  }

  public static boolean enableBreakpointsDuringEvaluation() {
    return Registry.is("debugger.enable.breakpoints.during.evaluation");
  }

  @Nullable
  public XDebugSession getXDebugSession() {
    JavaDebugProcess process = myDebugProcess.getXdebugProcess();
    return process != null ? process.getSession() : null;
  }
}
