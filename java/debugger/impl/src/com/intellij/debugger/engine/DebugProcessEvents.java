// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.agent.MemoryAgentUtil;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.InstrumentationTracker;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author lex
 */
public class DebugProcessEvents extends DebugProcessImpl {
  private static final Logger LOG = Logger.getInstance(DebugProcessEvents.class);
  private static final String REQUEST_HANDLER = "REQUEST_HANDLER";

  private Map<VirtualMachine, DebuggerEventThread> myEventThreads = new HashMap<>();

  public DebugProcessEvents(Project project) {
    super(project);
    DebuggerSettings.getInstance().addCapturePointsSettingsListener(this::createStackCapturingBreakpoints, myDisposable);
  }

  @Override
  protected void commitVM(final VirtualMachine vm) {
    super.commitVM(vm);
    if (vm != null) {
      vmAttached();
      if (vm.canBeModified()) {
        Factory<DebuggerEventThread> createEventThread = () -> new DebuggerEventThread();
        DebuggerEventThread eventThread = ContainerUtil.getOrCreate(myEventThreads, vm, createEventThread);
        ApplicationManager.getApplication().executeOnPooledThread(
          ConcurrencyUtil.underThreadNameRunnable("DebugProcessEvents", eventThread));
      }
    }
  }

  private static void showStatusText(DebugProcessEvents debugProcess,  Event event) {
    Requestor requestor = RequestManagerImpl.findRequestor(event.request());
    Breakpoint breakpoint = null;
    if(requestor instanceof Breakpoint) {
      breakpoint = (Breakpoint)requestor;
    }
    String text = debugProcess.getEventText(Pair.create(breakpoint, event));
    debugProcess.showStatusText(text);
  }

  public @Nls String getEventText(Pair<Breakpoint, Event> descriptor) {
    String text = "";
    final Event event = descriptor.getSecond();
    final Breakpoint breakpoint = descriptor.getFirst();
    if (event instanceof LocatableEvent) {
      try {
        text = breakpoint != null ? breakpoint.getEventMessage(((LocatableEvent)event)) : JavaDebuggerBundle
          .message("status.generic.breakpoint.reached");
      }
      catch (InternalException e) {
        text = JavaDebuggerBundle.message("status.generic.breakpoint.reached");
      }
    }
    else if (event instanceof VMStartEvent) {
      text = JavaDebuggerBundle.message("status.process.started");
    }
    else if (event instanceof VMDeathEvent) {
      text = JavaDebuggerBundle.message("status.process.terminated");
    }
    else if (event instanceof VMDisconnectEvent) {
      text = JavaDebuggerBundle.message("status.disconnected", DebuggerUtilsImpl.getConnectionDisplayName(getConnection()));
    }
    return text;
  }

  private class DebuggerEventThread implements Runnable {
    private final VirtualMachineProxyImpl myVmProxy;
    private final DebuggerManagerThreadImpl myDebuggerManagerThread;

    DebuggerEventThread() {
      myVmProxy = getVirtualMachineProxy();
      myDebuggerManagerThread = getManagerThread();
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
      try {
        EventQueue eventQueue = myVmProxy.eventQueue();
        while (!isStopped()) {
          try {
            final EventSet eventSet = eventQueue.remove();

            myDebuggerManagerThread.invokeAndWait(new DebuggerCommandImpl(PrioritizedTask.Priority.HIGH) {
              @Override
              protected void action() {
                int processed = 0;
                for (Event event : eventSet) {
                  if (myReturnValueWatcher != null && myReturnValueWatcher.isTrackingEnabled()) {
                    if (myReturnValueWatcher.processEvent(event)) {
                      processed++;
                      continue;
                    }
                  }
                  Consumer<? super Event> handler = getEventRequestHandler(event);
                  if (handler != null) {
                    handler.consume(event);
                    processed++;
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
                        notifySkippedBreakpoints(locatableEvent, true);
                        eventSet.resume();
                        return;
                      }
                    }
                  }
                }

                if (!isCurrentVirtualMachine(myVmProxy)) {
                  notifySkippedBreakpoints(locatableEvent, false);
                  eventSet.resume();
                  return;
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

                Set<ClassPrepareRequestor> notifiedClassPrepareEventRequestors = null;
                ReferenceType lastPreparedClass = null;

                for (Event event : eventSet) {
                  if (getEventRequestHandler(event) != null) { // handled before
                    getSuspendManager().voteResume(suspendContext);
                    continue;
                  }

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
                      if (notifiedClassPrepareEventRequestors == null) {
                        notifiedClassPrepareEventRequestors = new HashSet<>(eventSet.size());
                      }
                      ReferenceType type = ((ClassPrepareEvent)event).referenceType();
                      if (lastPreparedClass != null && !lastPreparedClass.equals(type)) {
                        LOG.error("EventSet contains ClassPrepareEvents for: " + lastPreparedClass + " and " + type);
                      }
                      lastPreparedClass = type;

                      processClassPrepareEvent(suspendContext, (ClassPrepareEvent)event, notifiedClassPrepareEventRequestors);
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
      }
    }

    private void invokeVMDeathEvent() {
      getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
        @Override
        protected void action() {
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

  private static Consumer<? super Event> getEventRequestHandler(Event event) {
    EventRequest request = event.request();
    Object property = request != null ? request.getProperty(REQUEST_HANDLER) : null;
    if (property instanceof Consumer) {
      //noinspection unchecked
      return ((Consumer<? super Event>)property);
    }
    return null;
  }

  public static void enableRequestWithHandler(EventRequest request, Consumer<? super Event> handler) {
    request.putProperty(REQUEST_HANDLER, handler);
    request.enable();
  }

  private static void enableNonSuspendingRequest(EventRequest request, Consumer<? super Event> handler) {
    request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
    enableRequestWithHandler(request, handler);
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
      boolean canBeModified = machineProxy.canBeModified();
      if (canBeModified) {
        final EventRequestManager requestManager = machineProxy.eventRequestManager();

        if (machineProxy.canGetMethodReturnValues()) {
          myReturnValueWatcher = new MethodReturnValueWatcher(requestManager, this);
        }

        enableNonSuspendingRequest(requestManager.createThreadStartRequest(),
                                   event -> {
                                     ThreadReference thread = ((ThreadStartEvent)event).thread();
                                     machineProxy.threadStarted(thread);
                                     myDebugProcessDispatcher.getMulticaster().threadStarted(this, thread);
                                   });

        enableNonSuspendingRequest(requestManager.createThreadDeathRequest(),
                                   event -> {
                                     ThreadReference thread = ((ThreadDeathEvent)event).thread();
                                     machineProxy.threadStopped(thread);
                                     myDebugProcessDispatcher.getMulticaster().threadStopped(this, thread);
                                   });
      }

      // fill position managers
      PositionManagerFactory.EP_NAME.extensions(getProject())
        .map(factory -> factory.createPositionManager(this))
        .filter(Objects::nonNull)
        .forEach(this::appendPositionManager);

      PositionManagerFactory.EP_NAME.getPoint(getProject()).addExtensionPointListener(new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull PositionManagerFactory extension, @NotNull PluginDescriptor pluginDescriptor) {
          PositionManager manager = extension.createPositionManager(DebugProcessEvents.this);
          if (manager != null) {
            appendPositionManager(manager);
          }
        }
      }, false, myDisposable);

      myDebugProcessDispatcher.getMulticaster().processAttached(this);

      if (canBeModified) {
        createStackCapturingBreakpoints();
        AsyncStacksUtils.setupAgent(this);
      }

      if (canBeModified) {
        MemoryAgentUtil.setupAgent(this);
      }

      XDebugSessionImpl session = (XDebugSessionImpl)getSession().getXDebugSession();

      // breakpoints should be initialized after all processAttached listeners work
      ApplicationManager.getApplication().runReadAction(() -> {
        if (session != null) {
          session.initBreakpoints();
        }
      });

      if (Registry.is("debugger.track.instrumentation", true) && canBeModified) {
        trackClassRedefinitions();
      }

      showStatusText(JavaDebuggerBundle.message("status.connected", DebuggerUtilsImpl.getConnectionDisplayName(getConnection())));
      LOG.debug("leave: processVMStartEvent()");

      if (session != null) {
        session.setReadOnly(!canBeModified);
        session.setPauseActionSupported(canBeModified);
      }

      if (!canBeModified) {
        myDebugProcessDispatcher.getMulticaster().paused(getSuspendManager().pushSuspendContext(EventRequest.SUSPEND_ALL, 0));
        UIUtil.invokeLaterIfNeeded(() -> XDebugSessionTab.showFramesView(session));
      }
    }
  }

  private void trackClassRedefinitions() {
    getManagerThread().invoke(PrioritizedTask.Priority.HIGH, () -> InstrumentationTracker.track(this));
  }

  private void createStackCapturingBreakpoints() {
    getManagerThread().invoke(PrioritizedTask.Priority.HIGH, () -> {
      StackCapturingLineBreakpoint.deleteAll(this);
      StackCapturingLineBreakpoint.createAll(this);
    });
  }

  private void processVMDeathEvent(SuspendContextImpl suspendContext, @Nullable Event event) {
    // do not destroy another process on reattach
    VirtualMachine vm = getVirtualMachineProxy().getVirtualMachine();
    if (isAttached() && (event == null || vm == event.virtualMachine())) {
      try {
        preprocessEvent(suspendContext, null);
        cancelRunToCursorBreakpoint();
      }
      finally {
        DebuggerEventThread eventThread = myEventThreads.get(vm);
        if (eventThread != null) {
          eventThread.stopListening();
          myEventThreads.remove(vm);
        }
        closeProcess(false);
      }
    }

    if(event != null) {
      showStatusText(this, event);
    }
  }

  private void processClassPrepareEvent(SuspendContextImpl suspendContext,
                                        ClassPrepareEvent event,
                                        Set<ClassPrepareRequestor> notifiedRequestors) {
    preprocessEvent(suspendContext, event.thread());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Class prepared: " + event.referenceType().name());
    }
    try {
      suspendContext.getDebugProcess().getRequestsManager().processClassPrepared(event, notifiedRequestors);
    }
    finally {
      getSuspendManager().voteResume(suspendContext);
    }
  }

  private void processStepEvent(SuspendContextImpl suspendContext, StepEvent event) {
    final ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    RequestHint hint = getRequestHint(event);

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
      stopWatchingMethodReturn();
      getSuspendManager().voteSuspend(suspendContext);
      if (hint != null) {
        final MethodFilter methodFilter = hint.getMethodFilter();
        if (methodFilter instanceof NamedMethodFilter && !hint.wasStepTargetMethodMatched()) {
          final String message =
            JavaDebuggerBundle.message("notification.method.has.not.been.called", ((NamedMethodFilter)methodFilter).getMethodName());
          XDebuggerManagerImpl.getNotificationGroup().createNotification(message, MessageType.INFO).notify(project);
        }
        if (hint.wasStepTargetMethodMatched()) {
          suspendContext.getDebugProcess().resetIgnoreSteppingFilters(event.location(), hint);
        }
      }
    }
  }

  private static RequestHint getRequestHint(Event event) {
    return (RequestHint)event.request().getProperty("hint");
  }

  private void processLocatableEvent(final SuspendContextImpl suspendContext, final LocatableEvent event) {
    ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //we use schedule to allow processing other events during processing this one
    //this is especially necessary if a method is breakpoint condition
    getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        final SuspendManager suspendManager = getSuspendManager();
        SuspendContextImpl evaluatingContext = SuspendManagerUtil.getEvaluatingContext(suspendManager, suspendContext.getThread());

        final LocatableEventRequestor requestor = (LocatableEventRequestor)RequestManagerImpl.findRequestor(event.request());
        if (evaluatingContext != null &&
            !(requestor instanceof InstrumentationTracker.InstrumentationMethodBreakpoint) &&
            !DebuggerSession.enableBreakpointsDuringEvaluation()) {
          notifySkippedBreakpoints(event, true);
          // is inside evaluation, so ignore any breakpoints
          suspendManager.voteResume(suspendContext);
          return;
        }

        boolean resumePreferred = requestor != null && DebuggerSettings.SUSPEND_NONE.equals(requestor.getSuspendPolicy());
        boolean requestHit = false;
        long start = requestor instanceof OverheadProducer && ((OverheadProducer)requestor).track() ? System.currentTimeMillis() : 0;
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
            final String message = JavaDebuggerBundle.message("error.evaluating.breakpoint.condition.or.action", displayName, ex.getMessage());
            considerRequestHit[0] = Messages.showYesNoDialog(getProject(), message, ex.getTitle(), Messages.getQuestionIcon()) == Messages.YES;
          }, ModalityState.NON_MODAL);
          requestHit = considerRequestHit[0];
          resumePreferred = !requestHit;
        }
        finally {
          if (start > 0) {
            OverheadTimings.add(DebugProcessEvents.this, (OverheadProducer)requestor,
                                requestHit || requestor instanceof StackCapturingLineBreakpoint ? 1 : 0,
                                System.currentTimeMillis() - start);
          }
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

        // special check for smart step into with this breakpoint inside the expressions
        EventSet eventSet = suspendContext.getEventSet();
        if (eventSet != null && eventSet.size() > 1) {
          List<StepEvent> stepEvents = StreamEx.of(eventSet).select(StepEvent.class).toList();
          if (!stepEvents.isEmpty()) {
            resumePreferred = resumePreferred ||
                              stepEvents.stream()
                                        .map(DebugProcessEvents::getRequestHint)
                                        .allMatch(h -> {
                                          if (h != null) {
                                            Integer depth = h.checkCurrentPosition(suspendContext, event.location());
                                            return depth != null && depth != RequestHint.STOP;
                                          }
                                          return false;
                                        });
          }
        }

        if(!requestHit || resumePreferred) {
          suspendManager.voteResume(suspendContext);
        }
        else {
          stopWatchingMethodReturn();
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

  private final AtomicBoolean myNotificationsCoolDown = new AtomicBoolean();

  private void notifySkippedBreakpoints(@Nullable LocatableEvent event, boolean isEvaluation) {
    if (event != null && myNotificationsCoolDown.compareAndSet(false, true)) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> myNotificationsCoolDown.set(false), 1, TimeUnit.SECONDS);
      String message = isEvaluation ? JavaDebuggerBundle.message("message.breakpoint.skipped", event.location())
                                    : JavaDebuggerBundle.message("message.breakpoint.skipped.other.thread", event.location());
      XDebuggerManagerImpl.getNotificationGroup()
        .createNotification(message, MessageType.WARNING)
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
