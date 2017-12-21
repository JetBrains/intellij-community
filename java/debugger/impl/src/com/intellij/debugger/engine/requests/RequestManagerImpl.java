/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.*;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author lex
 */
public class RequestManagerImpl extends DebugProcessAdapterImpl implements RequestManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.RequestManagerImpl");

  private static final Key CLASS_NAME = Key.create("ClassName");
  private static final Key<Requestor> REQUESTOR = Key.create("Requestor");

  private final DebugProcessImpl myDebugProcess;
  private final Map<Requestor, String> myRequestWarnings = new HashMap<>();

  private final Map<Requestor, Set<EventRequest>> myRequestorToBelongedRequests = new HashMap<>();
  private EventRequestManager myEventRequestManager;
  private @Nullable ThreadReference myFilterThread;

  public RequestManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(this);
  }

  public EventRequestManager getVMRequestManager() {
    return myEventRequestManager;
  }

  @Nullable
  public ThreadReference getFilterThread() {
    return myFilterThread;
  }

  public void setFilterThread(@Nullable final ThreadReference filterThread) {
    myFilterThread = filterThread;
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
  public Requestor findRequestor(EventRequest request) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return request != null? (Requestor)request.getProperty(REQUESTOR) : null;
  }

  private static void addClassFilter(EventRequest request, String pattern){
    if(request instanceof AccessWatchpointRequest){
      ((AccessWatchpointRequest) request).addClassFilter(pattern);
    }
    else if(request instanceof ExceptionRequest){
      ((ExceptionRequest) request).addClassFilter(pattern);
    }
    else if(request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof ModificationWatchpointRequest) {
      ((ModificationWatchpointRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassFilter(pattern);
    }
  }

  private static void addClassExclusionFilter(EventRequest request, String pattern){
    if(request instanceof AccessWatchpointRequest){
      ((AccessWatchpointRequest) request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof ExceptionRequest){
      ((ExceptionRequest) request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof ModificationWatchpointRequest) {
      ((ModificationWatchpointRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassExclusionFilter(pattern);
    }
  }

  private void addLocatableRequest(FilteredRequestor requestor, EventRequest request) {
    if(DebuggerSettings.SUSPEND_ALL.equals(requestor.getSuspendPolicy())) {
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

    if (requestor.isClassFiltersEnabled() && !(request instanceof BreakpointRequest) /*no built-in class filters support for breakpoint requests*/ ) {
      ClassFilter[] classFilters = requestor.getClassFilters();
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

      for (ClassFilter filter : requestor.getClassExclusionFilters()) {
        if (filter.isEnabled()) {
          addClassExclusionFilter(request, filter.getPattern());
        }
      }
    }

    registerRequestInternal(requestor, request);
  }

  public void registerRequestInternal(final Requestor requestor, final EventRequest request) {
    registerRequest(requestor, request);
    request.putProperty(REQUESTOR, requestor);
  }

  public void registerRequest(Requestor requestor, EventRequest request) {
    myRequestorToBelongedRequests.computeIfAbsent(requestor, r -> new HashSet<>()).add(request);
  }

  // requests creation
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
    if(!myDebugProcess.isAttached()) {
      return;
    }
    final Set<EventRequest> requests = myRequestorToBelongedRequests.remove(requestor);
    if(requests == null) {
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
        try {
          myEventRequestManager.deleteEventRequest(request);
        } catch (ArrayIndexOutOfBoundsException e) {
          LOG.error("Exception in EventRequestManager.deleteEventRequest", e, ThreadDumper.dumpThreadsToString());
        }
      }
      catch (InvalidRequestStateException ignored) {
        // request is already deleted
      }
      catch (InternalException e) {
        //noinspection StatementWithEmptyBody
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

  public void callbackOnPrepareClasses(final ClassPrepareRequestor requestor, final SourcePosition classPosition) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    List<ClassPrepareRequest> prepareRequests = myDebugProcess.getPositionManager().createPrepareRequests(requestor, classPosition);
    if(prepareRequests.isEmpty()) {
      setInvalid(requestor, DebuggerBundle.message("status.invalid.breakpoint.out.of.class"));
      return;
    }

    for (ClassPrepareRequest prepareRequest : prepareRequests) {
      if (prepareRequest != null) {
        registerRequest(requestor, prepareRequest);
        prepareRequest.enable();
      }
    }
  }

  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String classOrPatternToBeLoaded) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ClassPrepareRequest classPrepareRequest = createClassPrepareRequest(requestor, classOrPatternToBeLoaded);

    if (classPrepareRequest != null) {
      registerRequest(requestor, classPrepareRequest);
      classPrepareRequest.enable();
      if (LOG.isDebugEnabled()) {
        LOG.debug("classOrPatternToBeLoaded = " + classOrPatternToBeLoaded);
      }
    }
  }

  public void enableRequest(EventRequest request) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(findRequestor(request) != null);
    try {
      final ThreadReference filterThread = myFilterThread;
      if (filterThread != null) {
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
      request.enable();
    } catch (InternalException e) {
      switch (e.errorCode()) {
        case JvmtiError.DUPLICATE : LOG.info(e); break;

        case JvmtiError.NOT_FOUND : break;
        //event request not found
        //there could be no requests after hotswap

        default: LOG.error(e);
      }
    }
  }

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
    return findRequests(requestor).stream().anyMatch(r -> !(r instanceof ClassPrepareRequest));
  }

  public void processDetached(DebugProcessImpl process, boolean closedByUser) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myEventRequestManager = null;
    myRequestWarnings.clear();
    myRequestorToBelongedRequests.clear();
  }

  public void processAttached(DebugProcessImpl process) {
    myEventRequestManager = myDebugProcess.getVirtualMachineProxy().eventRequestManager();
  }

  public void processClassPrepared(final ClassPrepareEvent event) {
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
        if (LOG.isDebugEnabled()) {
          LOG.debug("requestor found " + refType.signature());
        }
        requestor.processClassPrepare(myDebugProcess, refType);
      }
    }
  }

  public void clearWarnings() {
    myRequestWarnings.clear();
  }
}
