// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.debugger.*;
import com.intellij.debugger.engine.evaluation.DebuggerImplicitEvaluationContextUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.statistics.DebuggerStatistics;
import com.intellij.debugger.statistics.StatisticsStorage;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.InstrumentationTracker;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.breakpoints.SyntheticBreakpoint;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.jetbrains.jdi.EventRequestManagerImpl;
import com.jetbrains.jdi.LocationImpl;
import com.jetbrains.jdi.ThreadReferenceImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.debugger.impl.DebuggerUtilsImpl.forEachSafe;

public class DebugProcessEvents extends DebugProcessImpl {
  private static final Logger LOG = Logger.getInstance(DebugProcessEvents.class);
  private static final String REQUEST_HANDLER = "REQUEST_HANDLER";

  private final Map<VirtualMachine, DebuggerEventThread> myEventThreads = new HashMap<>();

  public DebugProcessEvents(Project project) {
    super(project);
    DebuggerSettings.getInstance().addCapturePointsSettingsListener(this::createStackCapturingBreakpoints, disposable);
  }

  @Override
  protected @NotNull VirtualMachineProxyImpl commitVM(final VirtualMachine vm) {
    VirtualMachineProxyImpl proxy = super.commitVM(vm);
    if (vm != null) {
      vmAttached(proxy);
      if (vm.canBeModified()) {
        DebuggerEventThread eventThread = myEventThreads.computeIfAbsent(vm, __ -> new DebuggerEventThread(proxy));
        ApplicationManager.getApplication().executeOnPooledThread(
          ConcurrencyUtil.underThreadNameRunnable("DebugProcessEvents", eventThread));
      }
    }
    return proxy;
  }

  private static void showStatusText(DebugProcessEvents debugProcess, Event event) {
    Requestor requestor = RequestManagerImpl.findRequestor(event.request());
    Breakpoint<?> breakpoint = null;
    if (requestor instanceof Breakpoint<?> b) {
      breakpoint = b;
    }
    String text = debugProcess.getEventText(Pair.create(breakpoint, event));
    debugProcess.showStatusText(text);
  }

  public @Nls String getEventText(Pair<Breakpoint<?>, Event> descriptor) {
    String text = "";
    final Event event = descriptor.getSecond();
    final Breakpoint<?> breakpoint = descriptor.getFirst();
    if (event instanceof LocatableEvent locatableEvent) {
      try {
        text = breakpoint != null ? breakpoint.getEventMessage(locatableEvent) : JavaDebuggerBundle
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

    DebuggerEventThread(VirtualMachineProxyImpl proxy) {
      myVmProxy = proxy;
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
        EventQueue eventQueue = myVmProxy.getVirtualMachine().eventQueue();
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
                    handler.accept(event);
                    processed++;
                  }
                }

                if (processed == eventSet.size()) {
                  DebuggerUtilsAsync.resume(eventSet);
                  return;
                }

                LocatableEvent locatableEvent = getLocatableEvent(eventSet);

                if (skipEvent(locatableEvent)) {
                  return;
                }

                if (eventSet.suspendPolicy() == EventRequest.SUSPEND_ALL) {
                  // This `if` is necessary in the first place for resume-only current thread stepping.
                  // For other cases it would be just a workaround for possible problems, it reports them.

                  // So for the resume-only-current-thread stepping mode we replace the old placeholder context
                  // (which was needed only to "hold" other threads) with the new one.

                  // This will cancel all activities for the placeholder context (and stepping monitor also).

                  // Thus, from the point of view of the stepping, it becomes the same as the classic suspend-all stepping
                  // where the old context totally resumes and after the stepping the new one is creating.
                  ((SuspendManagerImpl)getSuspendManager()).resumeAllSuspendAllContexts(eventSet);
                }

                SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(eventSet);

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
                    if (event instanceof VMStartEvent startEvent) {
                      //Sun WTK fails when J2ME when event set is resumed on VMStartEvent
                      processVMStartEvent(suspendContext, startEvent);
                    }
                    else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                      processVMDeathEvent(suspendContext, event);
                    }
                    else if (event instanceof ClassPrepareEvent classPrepareEvent) {
                      if (eventSet.size() > 1) {
                        // check for more than one different thread
                        if (StreamEx.of(eventSet).select(ClassPrepareEvent.class).map(ClassPrepareEvent::thread).toSet().size() > 1) {
                          logError("Two different threads in ClassPrepareEvents: " + eventSet);
                        }
                      }
                      if (notifiedClassPrepareEventRequestors == null) {
                        notifiedClassPrepareEventRequestors = new HashSet<>(eventSet.size());
                      }
                      ReferenceType type = classPrepareEvent.referenceType();
                      if (lastPreparedClass != null && !lastPreparedClass.equals(type)) {
                        logError("EventSet contains ClassPrepareEvents for: " + lastPreparedClass + " and " + type);
                      }
                      lastPreparedClass = type;

                      processClassPrepareEvent(suspendContext, classPrepareEvent, notifiedClassPrepareEventRequestors);
                    }
                    else if (event instanceof LocatableEvent locEvent) {
                      preloadEventInfo(locEvent.thread(), locEvent.location());
                      if (eventSet.size() > 1) {
                        // check for more than one different thread
                        if (StreamEx.of(eventSet).select(LocatableEvent.class).map(LocatableEvent::thread).toSet().size() > 1) {
                          logError("Two different threads in LocatableEvents: " + eventSet);
                        }
                      }
                      //AccessWatchpointEvent, BreakpointEvent, ExceptionEvent, MethodEntryEvent, MethodExitEvent,
                      //ModificationWatchpointEvent, StepEvent, WatchpointEvent
                      if (event instanceof StepEvent stepEvent) {
                        processStepEvent(suspendContext, stepEvent);
                      }
                      else {
                        processLocatableEvent(suspendContext, locEvent);
                      }
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
                    logError("Top-level error during event processing", e);
                  }
                }
              }

              private boolean skipEvent(@Nullable LocatableEvent locatableEvent) {
                if (!isCurrentVirtualMachine(myVmProxy)) {
                  notifySkippedBreakpoints(locatableEvent, SkippedBreakpointReason.OTHER_VM);
                  DebuggerUtilsAsync.resume(eventSet);
                  return true;
                }

                return false;
              }

            });
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
    ThreadReferenceProxyImpl oldThread = suspendContext.getEventThread();
    suspendContext.setThread(thread);

    if (oldThread == null) {
      switch (suspendContext.getSuspendPolicy()) {
        case EventRequest.SUSPEND_ALL -> suspendContext.getVirtualMachineProxy().addedSuspendAllContext();
        case EventRequest.SUSPEND_EVENT_THREAD -> Objects.requireNonNull(suspendContext.getEventThread()).threadWasSuspended();
      }
      //this is the first event in the eventSet that we process
      suspendContext.getDebugProcess().beforeSuspend(suspendContext);
    }
  }

  private static Consumer<? super Event> getEventRequestHandler(Event event) {
    EventRequest request = event.request();
    Object property = request != null ? request.getProperty(REQUEST_HANDLER) : null;
    if (property instanceof Consumer consumer) {
      //noinspection unchecked
      return consumer;
    }
    return null;
  }

  public static void enableRequestWithHandler(EventRequest request, Consumer<? super Event> handler) {
    request.putProperty(REQUEST_HANDLER, handler);
    DebuggerUtilsAsync.setEnabled(request, true);
  }

  public static void enableNonSuspendingRequest(EventRequest request, Consumer<? super Event> handler) {
    request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
    enableRequestWithHandler(request, handler);
  }

  @ReviseWhenPortedToJDK(value = "21", description = "Call addPlatformThreadsOnlyFilter directly from ThreadDeathRequest and ThreadStartRequest")
  private static EventRequest platformThreadsOnly(EventRequest eventRequest) {
    try {
      eventRequest.getClass().getMethod("addPlatformThreadsOnlyFilter").invoke(eventRequest);
    }
    catch (Exception e) {
      if (eventRequest instanceof EventRequestManagerImpl.ThreadLifecycleEventRequestImpl lifecycleEventRequest) {
        lifecycleEventRequest.addPlatformThreadsOnlyFilter();
      }
    }
    return eventRequest;
  }

  private void processVMStartEvent(@NotNull SuspendContextImpl suspendContext, VMStartEvent event) {
    // force cache thread proxy as we do not receive a threadStart event for this thread
    suspendContext.getVirtualMachineProxy().threadStarted(event.thread());
    preprocessEvent(suspendContext, event.thread());

    LOG.debug("enter: processVMStartEvent()");

    showStatusText(this, event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void vmAttached(VirtualMachineProxyImpl machineProxy) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(!isAttached());
    if (myState.compareAndSet(State.INITIAL, State.ATTACHED)) {
      boolean canBeModified = machineProxy.canBeModified();
      if (canBeModified) {
        final EventRequestManager requestManager = machineProxy.eventRequestManager();

        if (machineProxy.canGetMethodReturnValues()) {
          myReturnValueWatcher = new MethodReturnValueWatcher(requestManager, this);
        }

        enableNonSuspendingRequest(platformThreadsOnly(requestManager.createThreadStartRequest()),
                                   event -> {
                                     ThreadReference thread = ((ThreadStartEvent)event).thread();
                                     machineProxy.threadStarted(thread);
                                     forEachSafe(myDebugProcessListeners, it -> it.threadStarted(this, thread));
                                   });

        enableNonSuspendingRequest(platformThreadsOnly(requestManager.createThreadDeathRequest()),
                                   event -> {
                                     ThreadReference thread = ((ThreadDeathEvent)event).thread();
                                     machineProxy.threadStopped(thread);
                                     forEachSafe(myDebugProcessListeners, it -> it.threadStopped(this, thread));
                                   });
      }

      // fill position managers and watch for dynamic changes
      PositionManagerFactory.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {
        final Map<PositionManagerFactory, PositionManager> mapping = new HashMap<>();

        @Override
        public void extensionAdded(@NotNull PositionManagerFactory extension, @NotNull PluginDescriptor pluginDescriptor) {
          // Called on DMT for the first time, but on unspecified thread for the next calls
          //noinspection deprecation
          getManagerThread().invoke(PrioritizedTask.Priority.NORMAL, () ->
          {
            PositionManager manager = extension.createPositionManager(DebugProcessEvents.this);
            if (manager != null) {
              mapping.put(extension, manager);
              appendPositionManager(manager);
            }
          });
        }

        @Override
        public void extensionRemoved(@NotNull PositionManagerFactory extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          getManagerThread().schedule(PrioritizedTask.Priority.NORMAL, () -> {
            PositionManager manager = mapping.remove(extension);
            if (manager != null) {
              myPositionManager.removePositionManager(manager);
            }
          });
        }
      }, true, disposable);

      forEachSafe(myDebugProcessListeners, it -> it.processAttached(this));

      if (canBeModified) {
        createStackCapturingBreakpoints();
        AsyncStacksUtils.setupAgent(this);
        CollectionBreakpointUtils.setupCollectionBreakpointAgent(this);
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
        SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(EventRequest.SUSPEND_ALL, 0);
        forEachSafe(myDebugProcessListeners, it -> it.paused(suspendContext));
        UIUtil.invokeLaterIfNeeded(() -> XDebugSessionTab.showFramesView(session));
      }
    }
  }

  private void trackClassRedefinitions() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InstrumentationTracker.track(this);
  }

  private void createStackCapturingBreakpoints() {
    getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
      StackCapturingLineBreakpoint.deleteAll(this);
      StackCapturingLineBreakpoint.createAll(this);
    });
  }

  private void processVMDeathEvent(@NotNull SuspendContextImpl suspendContext, @Nullable Event event) {
    // do not destroy another process on reattach
    if (isAttached()) {
      VirtualMachine vm = suspendContext.getVirtualMachineProxy().getVirtualMachine();
      if (event == null || vm == event.virtualMachine()) {
        try {
          preprocessEvent(suspendContext, null);
          cancelSteppingBreakpoints();
        }
        finally {
          DebuggerEventThread eventThread = myEventThreads.get(vm);
          if (eventThread != null) {
            eventThread.stopListening();
            myEventThreads.remove(vm);
          }
          closeCurrentProcess(false);
        }
      }
    }

    if (event != null) {
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

  private void processStepEvent(@NotNull SuspendContextImpl suspendContext, StepEvent event) {
    logSuspendContext(suspendContext, () -> "process step event");
    final ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    RequestHint hint = getRequestHint(event);
    Object commandToken = getCommandToken(event);

    removeStepRequests(suspendContext, thread);

    boolean shouldResume = false;

    final Project project = getProject();
    while (true) {
      if (hint != null) {
        final RequestHint currentHint = hint;
        final Ref<Integer> stepDepth = Ref.create();
        long timeMs = TimeoutUtil.measureExecutionTime(() -> stepDepth.set(currentHint.getNextStepDepth(suspendContext)));
        StatisticsStorage.addStepping(this, commandToken, timeMs);

        final int nextStepDepth = stepDepth.get();
        logSuspendContext(suspendContext, () -> "nextStepDepth is " + nextStepDepth);
        if (nextStepDepth == RequestHint.RESUME) {
          getSession().clearSteppingThrough();
          shouldResume = true;
        }
        else if (nextStepDepth == RequestHint.STOP) {
          if (hint.getParentHint() != null) {
            hint = hint.getParentHint();
            continue;
          }
        }
        else {
          final ThreadReferenceProxyImpl threadProxy = suspendContext.getEventThread();
          hint.doStep(this, suspendContext, threadProxy, hint.getSize(), nextStepDepth, commandToken);
          shouldResume = true;
        }

        if (!shouldResume && hint.isRestoreBreakpoints()) {
          DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().enableBreakpoints(this);
        }
      }

      if (shouldResume) {
        getSuspendManager().voteResume(suspendContext);
      }
      else {
        StatisticsStorage.stepRequestCompleted(this, commandToken);
        showStatusText("");
        stopWatchingMethodReturn();
        getSuspendManager().voteSuspend(suspendContext);
        if (hint != null) {
          final MethodFilter methodFilter = hint.getMethodFilter();
          if (methodFilter instanceof NamedMethodFilter namedMethodFilter && !hint.wasStepTargetMethodMatched()) {
            String methodName = namedMethodFilter.getMethodName();
            String message = JavaDebuggerBundle.message("notification.method.has.not.been.called", methodName);
            XDebuggerManagerImpl.getNotificationGroup().createNotification(message, MessageType.INFO).notify(project);
            DebuggerStatistics.logMethodSkippedDuringStepping(project, StatisticsStorage.getSteppingStatisticOrNull(commandToken));
          }
          if (hint.wasStepTargetMethodMatched()) {
            suspendContext.getDebugProcess().resetIgnoreSteppingFilters(event.location(), hint);
          }
        }
      }
      return;
    }
  }

  public static void removeStepRequests(@NotNull SuspendContextImpl suspendContext, @Nullable ThreadReference thread) {
    suspendContext.getDebugProcess().deleteStepRequests(suspendContext.getVirtualMachineProxy().eventRequestManager(), thread);
  }

  // Preload event info in "parallel" commands, to avoid sync jdwp requests after
  static void preloadEventInfo(ThreadReference thread, @Nullable Location location) {
    if (Registry.is("debugger.preload.event.info") && DebuggerUtilsAsync.isAsyncEnabled()) {
      List<CompletableFuture> commands = new ArrayList<>();
      if (thread instanceof ThreadReferenceImpl t) {
        commands.addAll(List.of(t.frameCountAsync(), t.nameAsync(), t.statusAsync(), t.frameAsync(0)));
      }
      if (location instanceof LocationImpl locationImpl) {
        commands.add(DebuggerUtilsEx.getMethodAsync(locationImpl));
      }
      try {
        CompletableFuture.allOf(commands.toArray(CompletableFuture[]::new)).get(1, TimeUnit.SECONDS);
      }
      catch (InterruptedException | TimeoutException ignored) {
      }
      catch (Exception e) {
        Throwable throwable = DebuggerUtilsAsync.unwrap(e);
        if (!(throwable instanceof IncompatibleThreadStateException)) {
          DebuggerUtilsAsync.logError(e);
        }
      }
    }
  }

  private static RequestHint getRequestHint(Event event) {
    return (RequestHint)event.request().getProperty("hint");
  }

  private static @Nullable Object getCommandToken(Event event) {
    return event.request().getProperty("commandToken");
  }

  private void processLocatableEvent(final SuspendContextImpl suspendContext, final LocatableEvent event) {
    ThreadReference thread = event.thread();
    //LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //we use schedule to allow processing other events during processing this one
    //this is especially necessary if a method is breakpoint condition
    suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        logSuspendContext(suspendContext, () -> "start locatable event processing");
        final SuspendManager suspendManager = getSuspendManager();

        final LocatableEventRequestor requestor = (LocatableEventRequestor)RequestManagerImpl.findRequestor(event.request());
        ThreadReferenceProxyImpl threadProxy = suspendContext.getThread();
        boolean isEvaluationOnCurrentThread = threadProxy != null && threadProxy.isEvaluating();
        if ((isEvaluationOnCurrentThread || myThreadBlockedMonitor.isInResumeAllMode()) &&
            !(requestor instanceof InstrumentationTracker.InstrumentationMethodBreakpoint) &&
            !DebuggerSession.enableBreakpointsDuringEvaluation()) {
          notifySkippedBreakpointInEvaluation(event, suspendContext);
          // is inside evaluation, so ignore any breakpoints
          logSuspendContext(suspendContext,
                            () -> "Resume because of evaluation: isEvaluationOnCurrentThread = " + isEvaluationOnCurrentThread +
                            ", myThreadBlockedMonitor.isInResumeAllMode() = " + myThreadBlockedMonitor.isInResumeAllMode());
          suspendManager.voteResume(suspendContext);
          return;
        }

        // Skip breakpoints in other threads during suspend-all stepping.
        //
        // Don't try to check breakpoint's condition or evaluate its log expression,
        // because these evaluations may lead to skipping of more important stepping events,
        // see IDEA-336282.
        boolean useThreadFiltering = requestor == null || !requestor.shouldIgnoreThreadFiltering();
        if (!DebuggerSession.filterBreakpointsDuringSteppingUsingDebuggerEngine() && useThreadFiltering) {
          LightOrRealThreadInfo filter = getRequestsManager().getFilterThread();
          if (filter != null) {
            if (myPreparingToSuspendAll || !filter.checkSameThread(thread, suspendContext)) {
              // notify only if the current session is not one with evaluations hidden from the user
              if (!checkContextIsFromImplicitThread(suspendContext)) {
                notifySkippedBreakpoints(event, SkippedBreakpointReason.STEPPING);
              }
              logSuspendContext(suspendContext, () -> "Skip breakpoint because of filter " + filter);
              suspendManager.voteResume(suspendContext);
              return;
            }
          }
        }

        boolean resumePreferred = requestor != null && DebuggerSettings.SUSPEND_NONE.equals(requestor.getSuspendPolicy());
        boolean requestHit = false;
        long startTimeNs = System.nanoTime();
        long endTimeNs = 0;
        try {
          if (event.request().isEnabled()) {
            requestHit = (requestor != null) && requestor.processLocatableEvent(this, event);
          }
        }
        catch (final LocatableEventRequestor.EventProcessingException ex) {
          // stop timer here to prevent reporting dialog opened time
          endTimeNs = System.nanoTime();
          if (LOG.isDebugEnabled()) {
            LOG.debug(ex.getMessage());
          }
          final boolean[] considerRequestHit = new boolean[]{true};
          DebuggerInvocationUtil.invokeAndWait(getProject(), () -> {
            final String displayName = requestor instanceof Breakpoint<?> breakpoint ? breakpoint.getDisplayName() : requestor.getClass().getSimpleName();
            final String message = JavaDebuggerBundle.message("error.evaluating.breakpoint.condition.or.action", displayName, ex.getMessage());
            considerRequestHit[0] = Messages.showYesNoDialog(getProject(), message, ex.getTitle(), Messages.getQuestionIcon()) == Messages.YES;
          }, ModalityState.nonModal());
          requestHit = considerRequestHit[0];
          resumePreferred = !requestHit;
        }
        catch (VMDisconnectedException e) {
          throw e;
        }
        catch (Exception e) { // catch everything else here to be able to vote
          logError("Error in requestor.processLocatableEvent", e);
        }
        finally {
          if (endTimeNs == 0) {
            endTimeNs = System.nanoTime();
          }
          long timeMs = TimeUnit.NANOSECONDS.toMillis(endTimeNs - startTimeNs);
          if (requestor instanceof Breakpoint<?> breakpoint) {
            DebuggerStatistics.logBreakpointVisit(breakpoint, timeMs);
          }
          if (requestor instanceof OverheadProducer overheadProducer && overheadProducer.track()) {
            OverheadTimings.add(DebugProcessEvents.this, overheadProducer,
                                requestHit || requestor instanceof StackCapturingLineBreakpoint ? 1 : 0,
                                timeMs);
          }
        }

        if (requestHit && requestor instanceof Breakpoint<?> breakpoint) {
          // if requestor is a breakpoint and this breakpoint was hit, no matter its suspend policy
          ApplicationManager.getApplication().runReadAction(() -> {
            XDebugSession session = getSession().getXDebugSession();
            if (session != null) {
              XBreakpoint<?> xBreakpoint = breakpoint.getXBreakpoint();
              if (xBreakpoint != null) {
                ((XDebugSessionImpl)session).processDependencies(xBreakpoint);
              }
            }
          });
        }

        // special check for smart step into with this breakpoint inside the expressions
        EventSet eventSet = suspendContext.getEventSet();
        if (eventSet != null && eventSet.size() > 1) {
          List<StepEvent> stepEvents = ContainerUtil.filterIsInstance(eventSet, StepEvent.class);
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

        if (!requestHit || resumePreferred) {
          boolean finalRequestHit = requestHit;
          boolean finalResumePreferred = resumePreferred;
          logSuspendContext(suspendContext, () -> "Resume: requestHit = " + finalRequestHit + ", resumePreferred = " + finalResumePreferred);
          suspendManager.voteResume(suspendContext);
        }
        else {
          logSuspendContext(suspendContext, () -> "suspend is expected");
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


  static boolean specialSuspendProcessingForAlwaysSwitch(@NotNull SuspendContextImpl suspendContext,
                                                         @NotNull SuspendManagerImpl suspendManager,
                                                         @NotNull ThreadReference thread) {
    DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
      debugProcess.logError("Special processing called for " + suspendContext);
      return false;
    }
    if (suspendContext.getSuspendPolicy() != EventRequest.SUSPEND_EVENT_THREAD) {
      debugProcess.logError("Suspend context " + suspendContext + " has non-thread suspend policy");
    }

    boolean noStandardSuspendNeeded;
    List<SuspendContextImpl> suspendAllContexts = suspendManager.getSuspendAllContexts();
    if (!suspendAllContexts.isEmpty()) {
      logSuspendContext(suspendContext, () -> "join with suspend-all context");
      if (suspendAllContexts.size() > 1) {
        debugProcess.logError("Many suspend all switch contexts: " + suspendAllContexts);
      }

      noStandardSuspendNeeded = true;
      ThreadReferenceProxyImpl threadProxy = suspendContext.getVirtualMachineProxy().getThreadReferenceProxy(thread);
      SuspendContextImpl firstSuspendAllContext = suspendAllContexts.get(0);
      if (suspendAllContexts.size() == 1 && firstSuspendAllContext.mySteppingThreadForResumeOneSteppingCurrentMode == threadProxy) {
        // Stepping in "Resume only one thread in suspend-all" mode met a breakpoint

        suspendManager.myExplicitlyResumedThreads.remove(threadProxy);
        suspendManager.scheduleResume(firstSuspendAllContext);

        suspendContext.getVirtualMachineProxy().suspend();
        // Inside switchToSuspendAll the engine will replace the placeholder context with the new one.
        // It is necessary to cancel all current activities with the placeholder context (and the stepping monitor also).
        SuspendOtherThreadsRequestor.switchToSuspendAll(suspendContext, (s) -> true);
      }
      else if (suspendManager.myExplicitlyResumedThreads.contains(threadProxy)) {
        for (SuspendContextImpl context : suspendAllContexts) {
          if (!context.suspends(threadProxy)) {
            suspendManager.suspendThread(context, threadProxy);
          }
        }
        suspendManager.myExplicitlyResumedThreads.remove(threadProxy);
        suspendManager.scheduleResume(suspendContext);
        SuspendManagerUtil.switchToThreadInSuspendAllContext(firstSuspendAllContext, threadProxy);
      }
      else {
        // Already stopped, so this is "remaining" event. Need to resume the event.
        List<SuspendContextImpl> suspendAllSwitchContexts =
          ContainerUtil.filter(suspendAllContexts, c -> c.mySuspendAllSwitchedContext);
        if (suspendAllSwitchContexts.size() != 1) {
          debugProcess.logError("Requires just one suspend all switch context, but have: " + suspendAllSwitchContexts);
        }
        if (thread.suspendCount() == 1) {
          // There are some errors in evaluation-resume-suspend logic
          debugProcess.logError("This means resuming thread " + thread + " to the running state for " + suspendContext);
        }
        LOG.warn("Yet another thread has been stopped: " + suspendContext);
        suspendManager.scheduleResume(suspendContext);
        debugProcess.notifyStoppedOtherThreads();
      }
    }
    else {
      logSuspendContext(suspendContext, () -> "initiate transfer to suspend-all");
      noStandardSuspendNeeded = SuspendOtherThreadsRequestor.initiateTransferToSuspendAll(suspendContext, c -> true);
    }

    return noStandardSuspendNeeded;
  }

  private final AtomicBoolean myNotificationsCoolDown = new AtomicBoolean();

  public enum SkippedBreakpointReason {
    EVALUATION_IN_ANOTHER_THREAD, // There are too many open problems if we would stop.
    EVALUATION_IN_THE_SAME_THREAD, // Breakpoint in the evaluated code
    OTHER_VM, // Rare case of reattaching debugger to different VMs.
    STEPPING, // Suspend-all stepping ignores breakpoints in other threads for the sake of ease-of-debug.
  }

  private void notifySkippedBreakpointInEvaluation(@Nullable LocatableEvent event, @NotNull SuspendContextImpl suspendContext) {
    // notify only if the current session is not one with evaluations hidden from the user
    if (checkContextIsFromImplicitThread(suspendContext)) {
      return;
    }

    SkippedBreakpointReason reason = SkippedBreakpointReason.EVALUATION_IN_ANOTHER_THREAD;
    if (event != null) {
      ThreadReferenceProxyImpl proxy = suspendContext.getVirtualMachineProxy().getThreadReferenceProxy(event.thread());
      if (proxy != null && proxy.isEvaluating()) {
        reason = SkippedBreakpointReason.EVALUATION_IN_THE_SAME_THREAD;
      }
    }
    notifySkippedBreakpoints(event, reason);
  }

  private boolean checkContextIsFromImplicitThread(@NotNull SuspendContextImpl eventContext) {
    DebugProcess debugProcess = eventContext.getDebugProcess();
    LightOrRealThreadInfo implicitThread = DebuggerImplicitEvaluationContextUtil.getImplicitEvaluationThread(debugProcess);
    LightOrRealThreadInfo filterThread = getRequestsManager().getFilterThread();
    ThreadReferenceProxy eventThread = eventContext.getThread();

    if (implicitThread == null || eventThread == null) {
      return false;
    }

    // Case 1: We have filter and implicit threads provided, they are different, hence, this skipped breakpoint is no use for the user
    if (filterThread != null && !implicitThread.checkSameThread(eventThread.getThreadReference(), eventContext)) {
      return true;
    }

    // Case 2: Implicit thread is provided, no filter thread, check correct hit
    return implicitThread.checkSameThread(eventThread.getThreadReference(), eventContext);
  }

  private void notifySkippedBreakpoints(@Nullable LocatableEvent event, SkippedBreakpointReason reason) {
    if (event == null) return;

    // IDE user is not intended to see notifications about our synthetic breakpoints.
    final Requestor requestor = RequestManagerImpl.findRequestor(event.request());
    if (requestor instanceof SyntheticBreakpoint) return;

    DebuggerStatistics.logBreakpointSkipped(getProject(), reason);

    if (!myNotificationsCoolDown.compareAndSet(false, true)) return;

    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> myNotificationsCoolDown.set(false), 1, TimeUnit.SECONDS);
    var message = switch (reason) {
      case EVALUATION_IN_ANOTHER_THREAD,
           EVALUATION_IN_THE_SAME_THREAD -> JavaDebuggerBundle.message("message.breakpoint.skipped.during.evaluation", event.location());
      case OTHER_VM -> JavaDebuggerBundle.message("message.breakpoint.skipped.other.vm", event.location());
      case STEPPING -> JavaDebuggerBundle.message("message.breakpoint.skipped.during.stepping.in.another.thread", event.location());
    };
    XDebuggerManagerImpl.getNotificationGroup()
      .createNotification(message, MessageType.WARNING)
      .addAction(NotificationAction.createSimpleExpiring(JavaDebuggerBundle.message("message.breakpoint.skipped.learn.more"), () -> {
        BrowserUtil.browse("https://www.jetbrains.com/help/idea/?skipped.breakpoints");
      }))
      .notify(getProject());
  }

  private static @Nullable LocatableEvent getLocatableEvent(EventSet eventSet) {
    return StreamEx.of(eventSet).select(LocatableEvent.class).findFirst().orElse(null);
  }

  private void processDefaultEvent(SuspendContextImpl suspendContext) {
    preprocessEvent(suspendContext, null);
    getSuspendManager().voteResume(suspendContext);
  }

  private static void logSuspendContext(@NotNull SuspendContextImpl suspendContext, @NotNull Supplier<String> message) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("For suspend context " + suspendContext + ": " + message.get());
    }
  }
}
