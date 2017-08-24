/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.sun.jdi.InternalException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ThreadDeathRequest;
import com.sun.jdi.request.ThreadStartRequest;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author lex
 */
public class DebugProcessEvents extends DebugProcessImpl {
  private static final Logger LOG = Logger.getInstance(DebugProcessEvents.class);

  private DebuggerEventThread myEventThread;

  public DebugProcessEvents(Project project) {
    super(project);
    DebuggerSettings.getInstance().addCapturePointsSettingsListener(this::createStackCapturingBreakpoints, myDisposable);
  }

  @Override
  protected void commitVM(final VirtualMachine vm) {
    super.commitVM(vm);
    if(vm != null) {
      vmAttached();
      myEventThread = new DebuggerEventThread();
      ApplicationManager.getApplication().executeOnPooledThread(myEventThread);
    }
  }

  private static void showStatusText(DebugProcessEvents debugProcess,  Event event) {
    Requestor requestor = debugProcess.getRequestsManager().findRequestor(event.request());
    Breakpoint breakpoint = null;
    if(requestor instanceof Breakpoint) {
      breakpoint = (Breakpoint)requestor;
    }
    String text = debugProcess.getEventText(Pair.create(breakpoint, event));
    debugProcess.showStatusText(text);
  }

  public String getEventText(Pair<Breakpoint, Event> descriptor) {
    String text = "";
    final Event event = descriptor.getSecond();
    final Breakpoint breakpoint = descriptor.getFirst();
    if (event instanceof LocatableEvent) {
      try {
        text = breakpoint != null ? breakpoint.getEventMessage(((LocatableEvent)event)) : DebuggerBundle.message("status.generic.breakpoint.reached");
      }
      catch (InternalException e) {
        text = DebuggerBundle.message("status.generic.breakpoint.reached");
      }
    }
    else if (event instanceof VMStartEvent) {
      text = DebuggerBundle.message("status.process.started");
    }
    else if (event instanceof VMDeathEvent) {
      text = DebuggerBundle.message("status.process.terminated");
    }
    else if (event instanceof VMDisconnectEvent) {
      final RemoteConnection connection = getConnection();
      final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
      final String transportName = DebuggerBundle.getTransportName(connection);
      text = DebuggerBundle.message("status.disconnected", addressDisplayName, transportName);
    }
    return text;
  }

  private class DebuggerEventThread implements Runnable {
    private final VirtualMachineProxyImpl myVmProxy;

    DebuggerEventThread () {
      myVmProxy = getVirtualMachineProxy();
    }

    private boolean myIsStopped = false;

    public synchronized void stopListening() {
      myIsStopped = true;
    }

    private synchronized boolean isStopped() {
      return myIsStopped;
    }

    @Override
    public void run() {
      String oldThreadName = Thread.currentThread().getName();
      Thread.currentThread().setName("DebugProcessEvents");

      try {
        EventQueue eventQueue = myVmProxy.eventQueue();
        while (!isStopped()) {
          try {
            final EventSet eventSet = eventQueue.remove();

            getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
              @Override
              public Priority getPriority() {
                return Priority.HIGH;
              }

              @Override
              protected void action() throws Exception {
                int processed = 0;
                for (Event event : eventSet) {
                  if (myReturnValueWatcher != null && myReturnValueWatcher.isEnabled()) {
                    if (myReturnValueWatcher.processEvent(event)) {
                      processed++;
                      continue;
                    }
                  }
                  if (event instanceof ThreadStartEvent) {
                    processed++;
                    ThreadReference thread = ((ThreadStartEvent)event).thread();
                    getVirtualMachineProxy().threadStarted(thread);
                    myDebugProcessDispatcher.getMulticaster().threadStarted(DebugProcessEvents.this, thread);
                  }
                  else if (event instanceof ThreadDeathEvent) {
                    processed++;
                    ThreadReference thread = ((ThreadDeathEvent)event).thread();
                    getVirtualMachineProxy().threadStopped(thread);
                    myDebugProcessDispatcher.getMulticaster().threadStopped(DebugProcessEvents.this, thread);
                  }
                }

                if (processed == eventSet.size()) {
                  eventSet.resume();
                  return;
                }

                LocatableEvent locatableEvent = getLocatableEvent(eventSet);
                if (eventSet.suspendPolicy() == EventRequest.SUSPEND_ALL) {
                  // check if there is already one request with policy SUSPEND_ALL
                  for (SuspendContextImpl context : getSuspendManager().getEventContexts()) {
                    if (context.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
                      if (isResumeOnlyCurrentThread() && locatableEvent != null && !context.isEvaluating()) {
                        // if step event is present - switch context
                        getSuspendManager().resume(context);
                        //((SuspendManagerImpl)getSuspendManager()).popContext(context);
                        continue;
                      }
                      if (!DebuggerSession.enableBreakpointsDuringEvaluation()) {
                        notifySkippedBreakpoints(locatableEvent);
                        eventSet.resume();
                        return;
                      }
                    }
                  }
                }

                SuspendContextImpl suspendContext = null;

                if (isResumeOnlyCurrentThread() && locatableEvent != null) {
                  for (SuspendContextImpl context : getSuspendManager().getEventContexts()) {
                    ThreadReferenceProxyImpl threadProxy = getVirtualMachineProxy().getThreadReferenceProxy(locatableEvent.thread());
                    if (context.getSuspendPolicy() == EventRequest.SUSPEND_ALL &&
                        context.isExplicitlyResumed(threadProxy)) {
                      context.myResumedThreads.remove(threadProxy);
                      suspendContext = context;
                      suspendContext.myVotesToVote = eventSet.size();
                      break;
                    }
                  }
                }

                if (suspendContext == null) {
                  suspendContext = getSuspendManager().pushSuspendContext(eventSet);
                }

                for (Event event : eventSet) {
                  //if (LOG.isDebugEnabled()) {
                  //  LOG.debug("EVENT : " + event);
                  //}
                  try {
                    if (event instanceof VMStartEvent) {
                      //Sun WTK fails when J2ME when event set is resumed on VMStartEvent
                      processVMStartEvent(suspendContext, (VMStartEvent)event);
                    }
                    else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                      processVMDeathEvent(suspendContext, event);
                    }
                    else if (event instanceof ClassPrepareEvent) {
                      processClassPrepareEvent(suspendContext, (ClassPrepareEvent)event);
                    }
                    //AccessWatchpointEvent, BreakpointEvent, ExceptionEvent, MethodEntryEvent, MethodExitEvent,
                    //ModificationWatchpointEvent, StepEvent, WatchpointEvent
                    else if (event instanceof StepEvent) {
                      processStepEvent(suspendContext, (StepEvent)event);
                    }
                    else if (event instanceof LocatableEvent) {
                      processLocatableEvent(suspendContext, (LocatableEvent)event);
                    }
                    else if (event instanceof ClassUnloadEvent) {
                      processDefaultEvent(suspendContext);
                    }
                  }
                  catch (VMDisconnectedException e) {
                    LOG.debug(e);
                  }
                  catch (InternalException e) {
                    LOG.info(e);
                  }
                  catch (Throwable e) {
                    LOG.error(e);
                  }
                }
              }
            });
          }
          catch (InternalException e) {
            LOG.debug(e);
          }
          catch (InterruptedException | ProcessCanceledException | VMDisconnectedException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.debug(e);
          }
        }
      }
      catch (InterruptedException | VMDisconnectedException e) {
        invokeVMDeathEvent();
      }
      finally {
        Thread.interrupted(); // reset interrupted status
        Thread.currentThread().setName(oldThreadName);
      }
    }

    private void invokeVMDeathEvent() {
      getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(EventRequest.SUSPEND_NONE, 1);
          processVMDeathEvent(suspendContext, null);
        }
      });
    }
  }

  private static void preprocessEvent(SuspendContextImpl suspendContext, ThreadReference thread) {
    ThreadReferenceProxyImpl oldThread = suspendContext.getThread();
    suspendContext.setThread(thread);

    if(oldThread == null) {
      //this is the first event in the eventSet that we process
      suspendContext.getDebugProcess().beforeSuspend(suspendContext);
    }
  }

  private void processVMStartEvent(final SuspendContextImpl suspendContext, VMStartEvent event) {
    preprocessEvent(suspendContext, event.thread());

    LOG.debug("enter: processVMStartEvent()");

    showStatusText(this, event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void vmAttached() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(!isAttached());
    if (myState.compareAndSet(State.INITIAL, State.ATTACHED)) {
      final VirtualMachineProxyImpl machineProxy = getVirtualMachineProxy();
      final EventRequestManager requestManager = machineProxy.eventRequestManager();

      if (machineProxy.canGetMethodReturnValues()) {
        myReturnValueWatcher = new MethodReturnValueWatcher(requestManager);
      }

      final ThreadStartRequest threadStartRequest = requestManager.createThreadStartRequest();
      threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
      threadStartRequest.enable();
      final ThreadDeathRequest threadDeathRequest = requestManager.createThreadDeathRequest();
      threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
      threadDeathRequest.enable();

      // fill position managers
      ((DebuggerManagerImpl)DebuggerManager.getInstance(getProject())).getCustomPositionManagerFactories()
        .map(factory -> factory.fun(this))
        .filter(Objects::nonNull)
        .forEach(this::appendPositionManager);
      Stream.of(Extensions.getExtensions(PositionManagerFactory.EP_NAME, getProject()))
        .map(factory -> factory.createPositionManager(this))
        .filter(Objects::nonNull)
        .forEach(this::appendPositionManager);

      myDebugProcessDispatcher.getMulticaster().processAttached(this);

      createStackCapturingBreakpoints();

      // breakpoints should be initialized after all processAttached listeners work
      ApplicationManager.getApplication().runReadAction(() -> {
        XDebugSession session = getSession().getXDebugSession();
        if (session != null) {
          session.initBreakpoints();
        }
      });

      final String addressDisplayName = DebuggerBundle.getAddressDisplayName(getConnection());
      final String transportName = DebuggerBundle.getTransportName(getConnection());
      showStatusText(DebuggerBundle.message("status.connected", addressDisplayName, transportName));
      LOG.debug("leave: processVMStartEvent()");
    }
  }

  private void createStackCapturingBreakpoints() {
    getManagerThread().invoke(new DebuggerCommandImpl() {
      @Override
      public Priority getPriority() {
        return Priority.HIGH;
      }

      @Override
      protected void action() {
        StackCapturingLineBreakpoint.deleteAll(DebugProcessEvents.this);
        StackCapturingLineBreakpoint.createAll(DebugProcessEvents.this);
      }
    });
  }

  private void processVMDeathEvent(SuspendContextImpl suspendContext, Event event) {
    try {
      preprocessEvent(suspendContext, null);
      cancelRunToCursorBreakpoint();
    }
    finally {
      if (myEventThread != null) {
        myEventThread.stopListening();
        myEventThread = null;
      }
      closeProcess(false);
    }

    if(event != null) {
      showStatusText(this, event);
    }
  }

  private void processClassPrepareEvent(SuspendContextImpl suspendContext, ClassPrepareEvent event) {
    preprocessEvent(suspendContext, event.thread());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Class prepared: " + event.referenceType().name());
    }
    suspendContext.getDebugProcess().getRequestsManager().processClassPrepared(event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void processStepEvent(SuspendContextImpl suspendContext, StepEvent event) {
    final ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //noinspection HardCodedStringLiteral
    RequestHint hint = (RequestHint)event.request().getProperty("hint");

    deleteStepRequests(event.thread());

    boolean shouldResume = false;

    final Project project = getProject();
    if (hint != null) {
      final int nextStepDepth = hint.getNextStepDepth(suspendContext);
      if (nextStepDepth == RequestHint.RESUME) {
        getSession().clearSteppingThrough();
        shouldResume = true;
      }
      else if (nextStepDepth != RequestHint.STOP) {
        final ThreadReferenceProxyImpl threadProxy = suspendContext.getThread();
        doStep(suspendContext, threadProxy, hint.getSize(), nextStepDepth, hint);
        shouldResume = true;
      }

      if(!shouldResume && hint.isRestoreBreakpoints()) {
        DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().enableBreakpoints(this);
      }
    }

    if(shouldResume) {
      getSuspendManager().voteResume(suspendContext);
    }
    else {
      showStatusText("");
      if (myReturnValueWatcher != null) {
        myReturnValueWatcher.disable();
      }
      getSuspendManager().voteSuspend(suspendContext);
      if (hint != null) {
        final MethodFilter methodFilter = hint.getMethodFilter();
        if (methodFilter instanceof NamedMethodFilter && !hint.wasStepTargetMethodMatched()) {
          final String message = "Method <b>" + ((NamedMethodFilter)methodFilter).getMethodName() + "()</b> has not been called";
          XDebugSessionImpl.NOTIFICATION_GROUP.createNotification(message, MessageType.INFO).notify(project);
        }
        if (hint.wasStepTargetMethodMatched() && hint.isResetIgnoreFilters()) {
          checkPositionNotFiltered(suspendContext.getThread(), filters -> mySession.resetIgnoreStepFiltersFlag());
        }
      }
    }
  }

  private void processLocatableEvent(final SuspendContextImpl suspendContext, final LocatableEvent event) {
    ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //we use schedule to allow processing other events during processing this one
    //this is especially necessary if a method is breakpoint condition
    getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction() throws Exception {
        final SuspendManager suspendManager = getSuspendManager();
        SuspendContextImpl evaluatingContext = SuspendManagerUtil.getEvaluatingContext(suspendManager, suspendContext.getThread());

        if (evaluatingContext != null && !DebuggerSession.enableBreakpointsDuringEvaluation()) {
          notifySkippedBreakpoints(event);
          // is inside evaluation, so ignore any breakpoints
          suspendManager.voteResume(suspendContext);
          return;
        }

        final LocatableEventRequestor requestor = (LocatableEventRequestor) getRequestsManager().findRequestor(event.request());

        boolean resumePreferred = requestor != null && DebuggerSettings.SUSPEND_NONE.equals(requestor.getSuspendPolicy());
        boolean requestHit;
        try {
          requestHit = (requestor != null) && requestor.processLocatableEvent(this, event);
        }
        catch (final LocatableEventRequestor.EventProcessingException ex) {
          if (LOG.isDebugEnabled()) {
            LOG.debug(ex.getMessage());
          }
          final boolean[] considerRequestHit = new boolean[]{true};
          DebuggerInvocationUtil.invokeAndWait(getProject(), () -> {
            final String displayName = requestor instanceof Breakpoint? ((Breakpoint)requestor).getDisplayName() : requestor.getClass().getSimpleName();
            final String message = DebuggerBundle.message("error.evaluating.breakpoint.condition.or.action", displayName, ex.getMessage());
            considerRequestHit[0] = Messages.showYesNoDialog(getProject(), message, ex.getTitle(), Messages.getQuestionIcon()) == Messages.YES;
          }, ModalityState.NON_MODAL);
          requestHit = considerRequestHit[0];
          resumePreferred = !requestHit;
        }

        if (requestHit && requestor instanceof Breakpoint) {
          // if requestor is a breakpoint and this breakpoint was hit, no matter its suspend policy
          ApplicationManager.getApplication().runReadAction(() -> {
            XDebugSession session = getSession().getXDebugSession();
            if (session != null) {
              XBreakpoint breakpoint = ((Breakpoint)requestor).getXBreakpoint();
              if (breakpoint != null) {
                ((XDebugSessionImpl)session).processDependencies(breakpoint);
              }
            }
          });
        }

        if(!requestHit || resumePreferred) {
          suspendManager.voteResume(suspendContext);
        }
        else {
          if (myReturnValueWatcher != null) {
            myReturnValueWatcher.disable();
          }
          //if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
          //  // there could be explicit resume as a result of call to voteSuspend()
          //  // e.g. when breakpoint was considered invalid, in that case the filter will be applied _after_
          //  // resuming and all breakpoints in other threads will be ignored.
          //  // As resume() implicitly cleares the filter, the filter must be always applied _before_ any resume() action happens
          //  myBreakpointManager.applyThreadFilter(DebugProcessEvents.this, event.thread());
          //}
          suspendManager.voteSuspend(suspendContext);
          showStatusText(DebugProcessEvents.this, event);
        }
      }
    });
  }

  private void notifySkippedBreakpoints(LocatableEvent event) {
    if (event != null) {
      XDebugSessionImpl.NOTIFICATION_GROUP
        .createNotification(DebuggerBundle.message("message.breakpoint.skipped", event.location()), MessageType.INFO)
        .notify(getProject());
    }
  }

  @Nullable
  private static LocatableEvent getLocatableEvent(EventSet eventSet) {
    return StreamEx.of(eventSet).select(LocatableEvent.class).findFirst().orElse(null);
  }

  private void processDefaultEvent(SuspendContextImpl suspendContext) {
    preprocessEvent(suspendContext, null);
    getSuspendManager().voteResume(suspendContext);
  }
}
