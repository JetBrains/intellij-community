// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class RequestManagerImpl extends DebugProcessAdapterImpl implements RequestManager {
  private static final Logger LOG = Logger.getInstance(RequestManagerImpl.class);

  private static final Key CLASS_NAME = Key.create("ClassName");
  private static final Key<Requestor> REQUESTOR = Key.create("Requestor");

  private final DebugProcessImpl myDebugProcess;
  private final Map<Requestor, String> myRequestWarnings = new HashMap<>();

  private final Map<Requestor, Set<EventRequest>> myRequestorToBelongedRequests = new HashMap<>();
  private EventRequestManager myEventRequestManager;

  /**
   * It specifies the thread performing suspend-all stepping.
   * All events in other threads are ignored.
   */
  private @Nullable LightOrRealThreadInfo myFilterThread;

  public RequestManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(this);
  }

  public EventRequestManager getVMRequestManager() {
    return myEventRequestManager;
  }

  @Nullable
  public LightOrRealThreadInfo getFilterThread() {
    return myFilterThread;
  }

  @Nullable
  public ThreadReference getFilterRealThread() {
    return myFilterThread != null ? myFilterThread.getRealThread() : null;
  }

  /** @deprecated Use setThreadFilter instead */
  @Deprecated
  public void setFilterThread(@Nullable final ThreadReference filterThread) {
    if (filterThread != null) {
      setThreadFilter(new RealThreadInfo(filterThread));
    }
    else {
      setThreadFilter(null);
    }
  }
  public void setThreadFilter(@Nullable final LightOrRealThreadInfo filter) {
    myFilterThread = filter;
  }

  public Set<EventRequest> findRequests(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Set<EventRequest> requestSet = myRequestorToBelongedRequests.get(requestor);
    if (requestSet == null) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(requestSet);
  }

  @Nullable
  public static Requestor findRequestor(EventRequest request) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return request != null ? (Requestor)request.getProperty(REQUESTOR) : null;
  }

  private static void addClassFilter(EventRequest request, String pattern) {
    if (request instanceof ExceptionRequest) {
      ((ExceptionRequest)request).addClassFilter(pattern);
    }
    else if (request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassFilter(pattern);
    }
    else if (request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassFilter(pattern);
    }
    else if (request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassFilter(pattern);
    }
  }

  private static void addClassExclusionFilter(EventRequest request, String pattern) {
    if (request instanceof ExceptionRequest) {
      ((ExceptionRequest)request).addClassExclusionFilter(pattern);
    }
    else if (request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassExclusionFilter(pattern);
    }
    else if (request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassExclusionFilter(pattern);
    }
    else if (request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassExclusionFilter(pattern);
    }
  }

  private void addLocatableRequest(FilteredRequestor requestor, EventRequest request) {
    if (DebuggerSettings.SUSPEND_ALL.equals(requestor.getSuspendPolicy())) {
      request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
    }
    else {
      //when requestor.SUSPEND_POLICY == SUSPEND_NONE
      //we should pause thread in order to evaluate conditions
      request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
    }

    // count filter has to be applied manually if condition is specified
    if (requestor.isCountFilterEnabled() && !requestor.isConditionEnabled()) {
      request.addCountFilter(requestor.getCountFilter());
    }

    if (requestor.isClassFiltersEnabled() && !(request instanceof BreakpointRequest) /*no built-in class filters support for breakpoint requests*/) {
      addClassFilters(request, requestor.getClassFilters(), requestor.getClassExclusionFilters());
    }

    registerRequestInternal(requestor, request);
  }

  public void addClassFilters(EventRequest request, ClassFilter[] classFilters, ClassFilter[] classExclusionFilters) {
    if (DebuggerUtilsEx.getEnabledNumber(classFilters) == 1) {
      for (final ClassFilter filter : classFilters) {
        if (!filter.isEnabled()) {
          continue;
        }
        final JVMName jvmClassName = ReadAction.compute(() -> {
          PsiClass psiClass = DebuggerUtils.findClass(filter.getPattern(), myDebugProcess.getProject(), myDebugProcess.getSearchScope());
          if (psiClass == null) {
            return null;
          }
          return JVMNameUtil.getJVMQualifiedName(psiClass);
        });
        String pattern = filter.getPattern();
        try {
          if (jvmClassName != null) {
            pattern = jvmClassName.getName(myDebugProcess);
          }
        }
        catch (EvaluateException ignored) {
        }

        addClassFilter(request, pattern);
        break; // adding more than one inclusion filter does not work, only events that satisfy ALL filters are placed in the event queue.
      }
    }

    for (ClassFilter filter : classExclusionFilters) {
      if (filter.isEnabled()) {
        addClassExclusionFilter(request, filter.getPattern());
      }
    }
  }

  public void registerRequestInternal(final Requestor requestor, final EventRequest request) {
    registerRequest(requestor, request);
    request.putProperty(REQUESTOR, requestor);
  }

  public void registerRequest(Requestor requestor, EventRequest request) {
    myRequestorToBelongedRequests.computeIfAbsent(requestor, r -> new HashSet<>()).add(request);
  }

  // requests creation
  @Override
  @Nullable
  public ClassPrepareRequest createClassPrepareRequest(ClassPrepareRequestor requestor, String pattern) {
    if (myEventRequestManager == null) { // detached already
      return null;
    }

    ClassPrepareRequest classPrepareRequest = myEventRequestManager.createClassPrepareRequest();
    classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
    if (!StringUtil.isEmpty(pattern)) {
      classPrepareRequest.addClassFilter(pattern);
      classPrepareRequest.putProperty(CLASS_NAME, pattern);
    }

    registerRequestInternal(requestor, classPrepareRequest);
    return classPrepareRequest;
  }

  public ExceptionRequest createExceptionRequest(FilteredRequestor requestor, ReferenceType referenceType, boolean notifyCaught, boolean notifyUnCaught) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ExceptionRequest req = myEventRequestManager.createExceptionRequest(referenceType, notifyCaught, notifyUnCaught);
    addLocatableRequest(requestor, req);
    return req;
  }

  public MethodEntryRequest createMethodEntryRequest(FilteredRequestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MethodEntryRequest req = myEventRequestManager.createMethodEntryRequest();
    addLocatableRequest(requestor, req);
    return req;
  }

  public MethodExitRequest createMethodExitRequest(FilteredRequestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MethodExitRequest req = myEventRequestManager.createMethodExitRequest();
    addLocatableRequest(requestor, req);
    return req;
  }

  public BreakpointRequest createBreakpointRequest(FilteredRequestor requestor, Location location) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    BreakpointRequest req = myEventRequestManager.createBreakpointRequest(location);
    addLocatableRequest(requestor, req);
    myRequestWarnings.remove(requestor);
    return req;
  }

  public AccessWatchpointRequest createAccessWatchpointRequest(FilteredRequestor requestor, Field field) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    AccessWatchpointRequest req = myEventRequestManager.createAccessWatchpointRequest(field);
    addLocatableRequest(requestor, req);
    return req;
  }

  public ModificationWatchpointRequest createModificationWatchpointRequest(FilteredRequestor requestor, Field field) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ModificationWatchpointRequest req = myEventRequestManager.createModificationWatchpointRequest(field);
    addLocatableRequest(requestor, req);
    return req;
  }

  public void deleteRequest(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myRequestWarnings.remove(requestor);
    if (!myDebugProcess.isAttached()) {
      return;
    }
    final Set<EventRequest> requests = myRequestorToBelongedRequests.remove(requestor);
    if (requests == null) {
      return;
    }
    for (final EventRequest request : requests) {
      try {
        final Requestor targetRequestor = (Requestor)request.getProperty(REQUESTOR);
        if (targetRequestor != requestor) {
          // the same request may be assigned to more than one requestor, but
          // there is only one 'targetRequestor' for each request, so if target requestor and requestor being processed are different,
          // should clear also the mapping targetRequestor->request
          Set<EventRequest> allTargetRequestorRequests = myRequestorToBelongedRequests.get(targetRequestor);
          if (allTargetRequestorRequests != null) {
            allTargetRequestorRequests.remove(request);
            if (allTargetRequestorRequests.isEmpty()) {
              myRequestorToBelongedRequests.remove(targetRequestor);
            }
          }
        }
        DebuggerUtilsAsync.deleteEventRequest(myEventRequestManager, request);
      }
      catch (InvalidRequestStateException ignored) {
        // request is already deleted
      }
      catch (InternalException e) {
        if (e.errorCode() == JvmtiError.NOT_FOUND) {
          //event request not found
          //there could be no requests after hotswap
        }
        else {
          LOG.info(e);
        }
      }
    }
  }

  private static Function<EventRequest, CompletableFuture<Void>> getEventRequestEnabler(boolean sync) {
    if (sync) {
      return r -> {
        r.enable();
        return CompletableFuture.completedFuture(null);
      };
    }
    else {
      return request -> DebuggerUtilsAsync.setEnabled(request, true);
    }
  }

  @Override
  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, SourcePosition classPosition) {
    callbackOnPrepareClasses(requestor, classPosition, getEventRequestEnabler(true));
  }

  public CompletableFuture<Void> callbackOnPrepareClassesAsync(ClassPrepareRequestor requestor, SourcePosition classPosition) {
    return callbackOnPrepareClasses(requestor, classPosition, getEventRequestEnabler(false));
  }

  private CompletableFuture<Void> callbackOnPrepareClasses(ClassPrepareRequestor requestor,
                                                           SourcePosition classPosition,
                                                           Function<EventRequest, CompletableFuture<Void>> enabler) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (checkReadOnly(requestor)) return CompletableFuture.completedFuture(null);

    List<ClassPrepareRequest> prepareRequests = myDebugProcess.getPositionManager().createPrepareRequests(requestor, classPosition);
    if (prepareRequests.isEmpty()) {
      setInvalid(requestor, JavaDebuggerBundle.message("status.invalid.breakpoint.out.of.class"));
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.allOf(StreamEx.of(prepareRequests)
                                     .nonNull()
                                     .peek(r -> registerRequest(requestor, r))
                                     .map(enabler)
                                     .toArray(CompletableFuture[]::new));
  }

  @Override
  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String classOrPatternToBeLoaded) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (checkReadOnly(requestor)) return;

    ClassPrepareRequest classPrepareRequest = createClassPrepareRequest(requestor, classOrPatternToBeLoaded);

    if (classPrepareRequest != null) {
      registerRequest(requestor, classPrepareRequest);
      classPrepareRequest.enable();
      if (LOG.isDebugEnabled()) {
        LOG.debug("classOrPatternToBeLoaded = " + classOrPatternToBeLoaded);
      }
    }
  }

  @Override
  public void enableRequest(EventRequest request) {
    enableRequest(request, getEventRequestEnabler(true));
  }

  public CompletableFuture<Void> enableRequestAsync(EventRequest request) {
    return enableRequest(request, getEventRequestEnabler(false));
  }

  private CompletableFuture<Void> enableRequest(EventRequest request, Function<EventRequest, CompletableFuture<Void>> enabler) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(findRequestor(request) != null);
    try {
      final ThreadReference filterThread = myFilterThread == null ? null : myFilterThread.getRealThread();
      if (filterThread != null && DebuggerSession.filterBreakpointsDuringSteppingUsingDebuggerEngine()) {
        if (request instanceof BreakpointRequest) {
          ((BreakpointRequest)request).addThreadFilter(filterThread);
        }
        else if (request instanceof MethodEntryRequest) {
          ((MethodEntryRequest)request).addThreadFilter(filterThread);
        }
        else if (request instanceof MethodExitRequest) {
          ((MethodExitRequest)request).addThreadFilter(filterThread);
        }
      }
      return enabler.apply(request);
    }
    catch (InternalException e) {
      switch (e.errorCode()) {
        case JvmtiError.DUPLICATE -> LOG.info(e);
        case JvmtiError.NOT_FOUND -> {
          //event request not found
          //there could be no requests after hotswap
        }

        default -> LOG.error(e);
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void setInvalid(Requestor requestor, String message) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //deleteRequest(requestor);
    //myRequestorToBelongedRequests.remove(requestor); // clear any mapping to empty set if any
    if (!isVerified(requestor)) {
      myRequestWarnings.put(requestor, message);
    }
  }

  public @Nullable String getWarning(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myRequestWarnings.get(requestor);
  }

  public boolean isVerified(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //ClassPrepareRequest is added in any case, so do not count it
    return ContainerUtil.exists(findRequests(requestor), r -> !(r instanceof ClassPrepareRequest));
  }

  @Override
  public void processDetached(DebugProcessImpl process, boolean closedByUser) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myEventRequestManager = null;
    myRequestWarnings.clear();
    myRequestorToBelongedRequests.clear();
  }

  @Override
  public void processAttached(DebugProcessImpl process) {
    if (myDebugProcess.getVirtualMachineProxy().canBeModified()) {
      myEventRequestManager = myDebugProcess.getVirtualMachineProxy().eventRequestManager();
    }
  }

  public void processClassPrepared(final ClassPrepareEvent event, Set<ClassPrepareRequestor> notifiedRequestors) {
    if (!myDebugProcess.isAttached()) {
      return;
    }

    final ReferenceType refType = event.referenceType();

    if (refType instanceof ClassType || refType instanceof InterfaceType) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("signature = " + refType.signature());
      }
      ClassPrepareRequestor requestor = (ClassPrepareRequestor)event.request().getProperty(REQUESTOR);
      if (requestor != null) {
        if (notifiedRequestors.add(requestor)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("requestor found " + refType.signature());
          }
          requestor.processClassPrepare(myDebugProcess, refType);
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("requestor " + requestor + " already notified " + refType.signature());
          }
        }
      }
    }
  }

  public void clearWarnings() {
    myRequestWarnings.clear();
  }

  public boolean checkReadOnly(Requestor requestor) {
    if (!myDebugProcess.getVirtualMachineProxy().canBeModified()) {
      setInvalid(requestor, "Not available in read only mode");
      return true;
    }
    return false;
  }
}
