package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.InternalException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodExitRequest;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 25, 2004
 * Time: 4:39:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebugProcessEvents extends DebugProcessImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessEvents");
  private DebuggerEventThread myEventThread;
  private final BreakpointManager myBreakpointManager;

  public DebugProcessEvents(Project project) {
    super(project);
    myBreakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
  }

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
    String text = debugProcess.getEventText(new Pair<Breakpoint, Event>(breakpoint, event));
    debugProcess.showStatusText(text);
  }

  public String getEventText(Pair<Breakpoint, Event> descriptor) {
    String text = "";
    final Event event = descriptor.getSecond();
    final Breakpoint breakpoint = descriptor.getFirst();
    if (event instanceof LocatableEvent) {
      if (breakpoint instanceof LineBreakpoint && !((LineBreakpoint)breakpoint).isVisible()) {
        text = DebuggerBundle.message("status.stopped.at.cursor");
      }
      else {
        try {
          text = breakpoint != null? breakpoint.getEventMessage(((LocatableEvent)event)) : DebuggerBundle.message("status.generic.breakpoint.reached");
        }
        catch (InternalException e) {
          text = DebuggerBundle.message("status.generic.breakpoint.reached");
        }
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
    final VirtualMachineProxyImpl myVmProxy;

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

    public void run() {
      try {
        EventQueue eventQueue = myVmProxy.eventQueue();
        while (!isStopped()) {
          try {
            final EventSet eventSet = eventQueue.remove();
            if (myReturnValueWatcher != null && myReturnValueWatcher.isTrackingEnabled()) {
              int processed = 0;
              for (EventIterator eventIterator = eventSet.eventIterator(); eventIterator.hasNext(); ) {
                final Event event = eventIterator.nextEvent();
                if (event instanceof MethodExitEvent) {
                  if (myReturnValueWatcher.processMethodExitEvent((MethodExitEvent)event)) {
                    processed++;
                  }
                }
              }
              if (processed == eventSet.size()) {
                eventSet.resume();
                continue;
              }
            }

            getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
              protected void action() throws Exception {
                final SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(eventSet);

                for (EventIterator eventIterator = eventSet.eventIterator(); eventIterator.hasNext(); ) {
                  final Event event = eventIterator.nextEvent();

                  //if (LOG.isDebugEnabled()) {
                  //  LOG.debug("EVENT : " + event);
                  //}
                  try {
                    if (event instanceof VMStartEvent) {
                      //Sun WTK fails when J2ME when event set is resumed on VMStartEvent
                      processVMStartEvent(suspendContext, (VMStartEvent)event);
                    }
                    else if (event instanceof VMDeathEvent) {
                      processVMDeathEvent(suspendContext, event);
                    }
                    else if (event instanceof VMDisconnectEvent) {
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
                    else if (event instanceof ClassUnloadEvent){
                      processDefaultEvent(suspendContext);
                    }
                  }
                  catch (VMDisconnectedException e) {
                    LOG.debug(e);
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
          catch (InterruptedException e) {
            throw e;
          }
          catch (VMDisconnectedException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.debug(e);
          }
        }
      }
      catch (InterruptedException e) {
        invokeVMDeathEvent();
      }
      catch (VMDisconnectedException e) {
        invokeVMDeathEvent();
      } finally {
        Thread.interrupted(); // reset interrupted status
      }
    }

    private void invokeVMDeathEvent() {
      getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
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

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processVMStartEvent()");
    }

    showStatusText(this, event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void vmAttached() {
    LOG.assertTrue(!isAttached());
    if(isDetached()) {
      return;
    }

    setIsAttached();

    final VirtualMachineProxyImpl machineProxy = getVirtualMachineProxy();
    if (machineProxy.canGetMethodReturnValues()) {
      MethodExitRequest request = machineProxy.eventRequestManager().createMethodExitRequest();
      request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
      myReturnValueWatcher = new MethodReturnValueWatcher(request);
    }

    DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().setInitialBreakpointsState();
    myDebugProcessDispatcher.getMulticaster().processAttached(this);

    final String addressDisplayName = DebuggerBundle.getAddressDisplayName(getConnection());
    final String transportName = DebuggerBundle.getTransportName(getConnection());
    showStatusText(DebuggerBundle.message("status.connected", addressDisplayName, transportName));
    if (LOG.isDebugEnabled()) {
      LOG.debug("leave: processVMStartEvent()");
    }
  }

  private void processVMDeathEvent(SuspendContextImpl suspendContext, Event event) {
    preprocessEvent(suspendContext, null);

    if (myEventThread != null) {
      myEventThread.stopListening();
      myEventThread = null;
    }

    cancelRunToCursorBreakpoint();

    closeProcess(false);

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
    ThreadReference thread = event.thread();
    LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //noinspection HardCodedStringLiteral
    RequestHint hint = (RequestHint)event.request().getProperty("hint");

    boolean shouldResume = false;

    if (hint != null) {
      final int nextStepDepth = hint.getNextStepDepth(suspendContext);
      if (nextStepDepth != RequestHint.STOP) {
        final ThreadReferenceProxyImpl threadProxy = suspendContext.getThread();
        doStep(suspendContext, threadProxy, nextStepDepth, hint);
        shouldResume = true;
      }

      if(!shouldResume && hint.isRestoreBreakpoints()) {
        DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().enableBreakpoints(this);
      }
    }

    if(shouldResume) {
      getSuspendManager().voteResume(suspendContext);
    }
    else {
      showStatusText("");
      if (myReturnValueWatcher != null) {
        myReturnValueWatcher.setTrackingEnabled(false);
      }
      getSuspendManager().voteSuspend(suspendContext);
    }
  }

  private void processLocatableEvent(final SuspendContextImpl suspendContext, final LocatableEvent event) {
    if (myReturnValueWatcher != null && event instanceof MethodExitEvent) {
      if (myReturnValueWatcher.processMethodExitEvent(((MethodExitEvent)event))) {
        return;
      }
    }

    ThreadReference thread = event.thread();
    LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //we use invokeLater to allow processing other events during processing this one
    //this is especially nesessary if a method is breakpoint condition
    getManagerThread().invokeLater(new SuspendContextCommandImpl(suspendContext) {
      public void contextAction() throws Exception {
        final SuspendManager suspendManager = getSuspendManager();
        SuspendContextImpl evaluatingContext = SuspendManagerUtil.getEvaluatingContext(suspendManager, getSuspendContext().getThread());

        if(evaluatingContext != null) {
          // is inside evaluation, so ignore any breakpoints
          suspendManager.voteResume(suspendContext);
          return;
        }

        LocatableEventRequestor requestor = (LocatableEventRequestor) getRequestsManager().findRequestor(event.request());

        final boolean requestorAsksResume = (requestor == null) || requestor.processLocatableEvent(this, event);
        final boolean userWantsResume = (requestor instanceof Breakpoint) && DebuggerSettings.SUSPEND_NONE.equals(((Breakpoint)requestor).SUSPEND_POLICY);

        if (requestor instanceof Breakpoint && !requestorAsksResume) {
          // if requestor is a breakpoint and this breakpoint was hit, no matter its suspend policy
          myBreakpointManager.processBreakpointHit((Breakpoint)requestor);
        }

        if(requestorAsksResume || userWantsResume) {
          suspendManager.voteResume(suspendContext);
        }
        else {
          if (myReturnValueWatcher != null) {
            myReturnValueWatcher.setTrackingEnabled(false);
          }
          if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
            // there could be explicit resume as a result of call to voteSuspend()
            // e.g. when breakpoint was considered invalid, in that case the filter will be applied _after_
            // resuming and all breakpoints in other threads will be ignored. 
            // As resume() implicitly cleares the filter, the filter must be always applied _before_ any resume() action happens 
            myBreakpointManager.applyThreadFilter(DebugProcessEvents.this, event.thread());
          }
          suspendManager.voteSuspend(suspendContext);
          showStatusText(DebugProcessEvents.this, event);
        }
      }
    });
  }

  private void processDefaultEvent(SuspendContextImpl suspendContext) {
    preprocessEvent(suspendContext, null);
    getSuspendManager().voteResume(suspendContext);
  }
}
