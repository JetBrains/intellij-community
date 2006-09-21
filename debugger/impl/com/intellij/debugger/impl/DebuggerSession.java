package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationListener;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
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
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DebuggerSession {
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

  private boolean myIsEvaluating;

  private DebuggerSessionState myState = null;

  private final String mySessionName;
  private final DebugProcessImpl myDebugProcess;
  private @NotNull GlobalSearchScope mySearchScope;

  private final DebuggerContextImpl SESSION_EMPTY_CONTEXT;
  //Thread, user is currently stepping through
  private Set<ThreadReferenceProxyImpl> mySteppingThroughThreads = new HashSet<ThreadReferenceProxyImpl>();
  private boolean myCompileBeforeRunning;

  public boolean isSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    return mySteppingThroughThreads.contains(threadProxy);
  }

  @NotNull
  public GlobalSearchScope getSearchScope() {
    LOG.assertTrue(mySearchScope != null, "Accessing Session's search scope before its initialization");
    return mySearchScope;
  }

  private class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerContextImpl myDebuggerContext;

    MyDebuggerStateManager() {
      myDebuggerContext = SESSION_EMPTY_CONTEXT;
    }

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
    public void setState(final DebuggerContextImpl context, final int state, final int event, final String description) {
      LOG.assertTrue(SwingUtilities.isEventDispatchThread());
      LOG.assertTrue(context.getDebuggerSession() == DebuggerSession.this || context.getDebuggerSession() == null);
      final Runnable setStateRunnable = new Runnable() {
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
        getProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
          public void threadAction() {
            context.initCaches();
            DebuggerInvocationUtil.invokeLater(getProject(), setStateRunnable);
          }
        });
      }
    }
  }

  public boolean isCompileBeforeRunning() {
    return myCompileBeforeRunning;
  }

  protected DebuggerSession(String sessionName, final DebugProcessImpl debugProcess, boolean compileBeforeRunning) {
    mySessionName  = sessionName;
    myDebugProcess = debugProcess;
    myCompileBeforeRunning = compileBeforeRunning;
    SESSION_EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, null, null, null);
    myContextManager = new MyDebuggerStateManager();
    myState = new DebuggerSessionState(STATE_STOPPED, null);
    myDebugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      //executed in manager thread
      public void connectorIsReady() {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            RemoteConnection connection = myDebugProcess.getConnection();
            final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
            final String transportName = DebuggerBundle.getTransportName(connection);
            final String connectionStatusMessage = connection.isServerMode() ? DebuggerBundle.message("status.listening", addressDisplayName, transportName) : DebuggerBundle.message("status.connecting", addressDisplayName, transportName);
            getContextManager().setState(SESSION_EMPTY_CONTEXT, DebuggerSession.STATE_WAITING_ATTACH, DebuggerSession.EVENT_START_WAIT_ATTACH, connectionStatusMessage);
          }
        });
      }

      public void paused(final SuspendContextImpl suspendContext) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("paused");
        }

        ThreadReferenceProxyImpl currentThread   = suspendContext.getThread();
        final StackFrameContext        positionContext;

        if (currentThread == null) {
          //Pause pressed
          LOG.assertTrue(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
          SuspendContextImpl oldContext = getProcess().getSuspendManager().getPausedContext();

          if (oldContext != null) {
            currentThread = oldContext.getThread();
          }

          if(currentThread == null) {
            final Iterator<ThreadReferenceProxyImpl> iterator = getProcess().getVirtualMachineProxy().allThreads().iterator();
            while (iterator.hasNext()) {
              currentThread = iterator.next();
              if (currentThread.status() == ThreadReference.THREAD_STATUS_RUNNING) {
                break;
              }
            }
          }

          StackFrameProxyImpl proxy = null;
          if (currentThread != null) {
            while (!currentThread.isSuspended()) {
              // wait until thread is considered suspended. Querying data from a thread immediately after VM.suspend()
              // may result in IncompatibleThreadStateException, most likely some time after suspend() VM erroneously thinks that thread is still running
              try {
                Thread.sleep(10);
              }
              catch (InterruptedException e) {
              }
              if (currentThread.isCollected()) {
                break;
              }
            }

            try {
              proxy = (currentThread.frameCount() > 0) ? currentThread.frame(0) : null;
            }
            catch (EvaluateException e) {
              proxy = null;
              LOG.error(e);
            }
          }
          positionContext = new SimpleStackFrameContext(proxy, debugProcess);
        }
        else {
          positionContext = suspendContext;
        }

        final SourcePosition position = PsiDocumentManager.getInstance(getProject()).commitAndRunReadAction(new Computable<SourcePosition>() {
          public SourcePosition compute() {
            return ContextUtil.getSourcePosition(positionContext);
          }
        });

        if (position != null) {
          ArrayList<LineBreakpoint> toDelete = new ArrayList<LineBreakpoint>();

          java.util.List<Pair<Breakpoint, com.sun.jdi.event.Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
          for (Iterator<Pair<Breakpoint, com.sun.jdi.event.Event>> iterator = eventDescriptors.iterator(); iterator.hasNext();) {
            Pair<Breakpoint, com.sun.jdi.event.Event> eventDescriptor = iterator.next();
            Breakpoint breakpoint = eventDescriptor.getFirst();
            if (breakpoint instanceof LineBreakpoint) {
              SourcePosition sourcePosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
              if (sourcePosition == null || sourcePosition.getLine() != position.getLine()) {
                toDelete.add((LineBreakpoint)breakpoint);
              }
            }
          }

          RequestManagerImpl requestsManager = suspendContext.getDebugProcess().getRequestsManager();
          for (Iterator<LineBreakpoint> iterator = toDelete.iterator(); iterator.hasNext();) {
            BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
            requestsManager.deleteRequest(breakpointWithHighlighter);
            requestsManager.setInvalid(breakpointWithHighlighter, DebuggerBundle.message("error.invalid.breakpoint.source.changed"));
            breakpointWithHighlighter.updateUI();
          }

          if (toDelete.size() > 0 && toDelete.size() == eventDescriptors.size()) {
            getProcess().getManagerThread().invokeLater(myDebugProcess.createResumeCommand(suspendContext));
            return;
          }
        }

        final DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, suspendContext, currentThread, null);
        debuggerContext.setPositionCache(position);

        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            getContextManager().setState(debuggerContext, STATE_PAUSED, EVENT_PAUSE, null);
          }
        });
      }

      public void resumed(final SuspendContextImpl suspendContext) {
        final SuspendContextImpl currentContext = getProcess().getSuspendManager().getPausedContext();
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            if (currentContext != null) {
              getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, currentContext), STATE_PAUSED, EVENT_REFRESH, null);
            }
            else {
              getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_REFRESH, null);
            }
          }
        });
      }

      public void processAttached(final DebugProcessImpl process) {
        final RemoteConnection connection = getProcess().getConnection();
        final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
        final String transportName = DebuggerBundle.getTransportName(connection);
        final String message = DebuggerBundle.message("status.connected", addressDisplayName, transportName);

        process.getExecutionResult().getProcessHandler().notifyTextAvailable(message + "\n", ProcessOutputTypes.SYSTEM);
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_ATTACHED, message);
          }
        });
      }

      public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
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

      public void processDetached(final DebugProcessImpl debugProcess, boolean closedByUser) {
        if (!closedByUser) {
          ExecutionResult executionResult = debugProcess.getExecutionResult();
          if(executionResult != null) {
            final RemoteConnection connection = getProcess().getConnection();
            final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
            final String transportName = DebuggerBundle.getTransportName(connection);
            executionResult.getProcessHandler().notifyTextAvailable(DebuggerBundle.message("status.disconnected", addressDisplayName, transportName) + "\n", ProcessOutputTypes.SYSTEM);
          }
        }
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            final RemoteConnection connection = getProcess().getConnection();
            final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
            final String transportName = DebuggerBundle.getTransportName(connection);
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_STOPPED, EVENT_DETACHED, DebuggerBundle.message("status.disconnected", addressDisplayName, transportName));
          }
        });
        mySteppingThroughThreads.clear();
      }
    });

    myDebugProcess.addEvaluationListener(new EvaluationListener() {
      public void evaluationStarted(SuspendContextImpl context) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myIsEvaluating = true;
          }
        });
      }

      public void evaluationFinished(final SuspendContextImpl context) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            myIsEvaluating = false;
            if (context != getSuspendContext()) {
              getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, context), STATE_PAUSED, EVENT_REFRESH, null);
            }
          }
        });
      }
    });
  }

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

  private DebuggerSessionState getSessionState() {
    return myState;
  }

  public int getState() {
    return getSessionState().myState;
  }

  public String getStateDescription() {
    DebuggerSessionState state = getSessionState();
    if (state.myDescription != null) {
      return state.myDescription;
    }

    switch (state.myState) {
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
    return state.myDescription;
  }

  /* Stepping */
  private void resumeAction(final SuspendContextCommandImpl command, int event) {
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAIT_EVALUATION, event, null);
    myDebugProcess.getManagerThread().invokeLater(command, DebuggerManagerThreadImpl.HIGH_PRIORITY);
  }

  public void stepOut() {
    final SuspendContextImpl suspendContext = getSuspendContext();
    mySteppingThroughThreads.add(suspendContext.getThread());
    resumeAction(myDebugProcess.createStepOutCommand(suspendContext), EVENT_STEP);
  }

  public void stepOver(boolean ignoreBreakpoints) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    mySteppingThroughThreads.add(suspendContext.getThread());
    resumeAction(myDebugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints), EVENT_STEP);
  }

  public void stepInto(final boolean ignoreFilters) {
    final SuspendContextImpl suspendContext = getSuspendContext();
    mySteppingThroughThreads.add(suspendContext.getThread());
    resumeAction(myDebugProcess.createStepIntoCommand(suspendContext, ignoreFilters), EVENT_STEP);
  }

  public void runToCursor(Document document, int line, final boolean ignoreBreakpoints) {
    try {
      SuspendContextCommandImpl runToCursorCommand = myDebugProcess.createRunToCursorCommand(getSuspendContext(), document, line,
                                                                                             ignoreBreakpoints);
      mySteppingThroughThreads.add(getSuspendContext().getThread());
      resumeAction(runToCursorCommand, EVENT_STEP);
    }
    catch (EvaluateException e) {
      Messages.showErrorDialog(e.getMessage(), ActionsBundle.actionText(DebuggerActions.RUN_TO_CURSOR));
    }
  }


  public void resume() {
    if(getSuspendContext() != null) {
      mySteppingThroughThreads.remove(getSuspendContext().getThread());
      resumeAction(myDebugProcess.createResumeCommand(getSuspendContext()), EVENT_RESUME);
    }
  }

  public void pause() {
    myDebugProcess.getManagerThread().invokeLater(myDebugProcess.createPauseCommand());
  }

  /*Presentation*/

  public void showExecutionPoint() {
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, getSuspendContext()), STATE_PAUSED, EVENT_REFRESH, null);
  }

  public void refresh() {
    if (getState() == DebuggerSession.STATE_PAUSED) {
      DebuggerContextImpl context = myContextManager.getContext();
      DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(this, context.getSuspendContext(), context.getThreadProxy(), context.getFrameProxy());
      myContextManager.setState(newContext, DebuggerSession.STATE_PAUSED, EVENT_REFRESH, null);
    }
  }

  public void dispose() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    getProcess().dispose();
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_DISPOSED, EVENT_DISPOSE, null);
  }

  // ManagerCommands
  public boolean isStopped() {
    return getState() == STATE_STOPPED;
  }

  public boolean isAttached() {
    return !isStopped() && getState() != STATE_WAITING_ATTACH;
  }

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
    return getState() == STATE_RUNNING && !getProcess().getExecutionResult().getProcessHandler().isProcessTerminated();
  }

  private SuspendContextImpl getSuspendContext() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    return getContextManager().getContext().getSuspendContext();
  }

  protected @Nullable ExecutionResult attach(final RunProfileState state, final RemoteConnection remoteConnection, final boolean pollConnection) throws ExecutionException {
    final ExecutionResult executionResult = myDebugProcess.attachVirtualMachine(this, state, remoteConnection, pollConnection);
    final String addressDisplayName = DebuggerBundle.getAddressDisplayName(remoteConnection);
    final String transportName = DebuggerBundle.getTransportName(remoteConnection);
    final Module[] modules = state.getModulesToCompile();
    if (modules == null || modules.length == 0) {
      mySearchScope = GlobalSearchScope.allScope(getProject());
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(modules[0], true);
      for (int idx = 1; idx < modules.length; idx++) {
        Module module = modules[idx];
        scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(module, true));
      }
      mySearchScope = scope;
    }
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAITING_ATTACH, EVENT_START_WAIT_ATTACH, DebuggerBundle.message("status.waiting.attach", addressDisplayName, transportName));
    return executionResult;
  }
}