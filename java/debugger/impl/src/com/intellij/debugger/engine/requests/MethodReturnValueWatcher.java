/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.SimpleColoredComponent;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class MethodReturnValueWatcher implements OverheadProducer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.requests.MethodReturnValueWatcher");
  private @Nullable Method myLastExecutedMethod;
  private @Nullable Value myLastMethodReturnValue;

  private ThreadReference myThread;
  private @Nullable MethodEntryRequest myEntryRequest;
  private @Nullable Method myEntryMethod;
  private @Nullable MethodExitRequest myExitRequest;

  private volatile boolean myTrackingEnabled;
  private final EventRequestManager myRequestManager;
  private final DebugProcess myProcess;

  public MethodReturnValueWatcher(EventRequestManager requestManager, DebugProcess process) {
    myRequestManager = requestManager;
    myProcess = process;
  }

  private void processMethodExitEvent(MethodExitEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("<- " + event.method());
    }
    try {
      if (Registry.is("debugger.watch.return.speedup") && Comparing.equal(myEntryMethod, event.method())) {
        LOG.debug("Now watching all");
        enableEntryWatching(true);
        createExitRequest().enable();
      }
      final Method method = event.method();
      final Value retVal = event.returnValue();

      if (method == null || !DebuggerUtilsEx.isVoid(method)) {
        // remember methods with non-void return types only
        myLastExecutedMethod = method;
        myLastMethodReturnValue = retVal;
      }
    }
    catch (UnsupportedOperationException ex) {
      LOG.error(ex);
    }
  }

  private void processMethodEntryEvent(MethodEntryEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("-> " + event.method());
    }
    try {
      if (myEntryRequest != null && myEntryRequest.isEnabled()) {
        myExitRequest = createExitRequest();
        myExitRequest.addClassFilter(event.method().declaringType());
        myEntryMethod = event.method();
        myExitRequest.enable();

        if (LOG.isDebugEnabled()) {
          LOG.debug("Now watching only " + event.method());
        }

        enableEntryWatching(false);
      }
    }
    catch (VMDisconnectedException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void enableEntryWatching(boolean enable) {
    if (myEntryRequest != null) {
      myEntryRequest.setEnabled(enable);
    }
  }

  @Nullable
  public Method getLastExecutedMethod() {
    return myLastExecutedMethod;
  }

  @Nullable
  public Value getLastMethodReturnValue() {
    return myLastMethodReturnValue;
  }

  public boolean isEnabled() {
    return DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
  }

  public void setEnabled(final boolean enabled) {
    DebuggerSettings.getInstance().WATCH_RETURN_VALUES = enabled;
    clear();
  }

  public boolean isTrackingEnabled() {
    return myTrackingEnabled;
  }

  public void enable(ThreadReference thread) {
    setTrackingEnabled(true, thread);
  }
  
  public void disable() {
    setTrackingEnabled(false, null);
  }
  
  private void setTrackingEnabled(boolean trackingEnabled, final ThreadReference thread) {
    myTrackingEnabled = trackingEnabled;
    updateRequestState(trackingEnabled && isEnabled(), thread);
  }

  public void clear() {
    myLastExecutedMethod = null;
    myLastMethodReturnValue = null;
    myThread = null;
  }

  private void updateRequestState(final boolean enabled, @Nullable final ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (myEntryRequest != null) {
        myRequestManager.deleteEventRequest(myEntryRequest);
        myEntryRequest = null;
      }
      if (myExitRequest != null) {
        myRequestManager.deleteEventRequest(myExitRequest);
        myExitRequest = null;
      }
      if (enabled) {
        OverheadTimings.add(myProcess, this, 1, null);
        clear();
        myThread = thread;

        if (Registry.is("debugger.watch.return.speedup")) {
          createEntryRequest().enable();
        }
        createExitRequest().enable();
      }
    }
    catch (ObjectCollectedException ignored) {
    }
  }

  private static final String WATCHER_REQUEST_KEY = "WATCHER_REQUEST_KEY";

  private MethodEntryRequest createEntryRequest() {
    DebuggerManagerThreadImpl.assertIsManagerThread(); // to ensure EventRequestManager synchronization
    myEntryRequest = prepareRequest(myRequestManager.createMethodEntryRequest());
    return myEntryRequest;
  }

  @NotNull
  private MethodExitRequest createExitRequest() {
    DebuggerManagerThreadImpl.assertIsManagerThread(); // to ensure EventRequestManager synchronization
    if (myExitRequest != null) {
      myRequestManager.deleteEventRequest(myExitRequest);
    }
    myExitRequest = prepareRequest(myRequestManager.createMethodExitRequest());
    return myExitRequest;
  }

  @NotNull
  private <T extends EventRequest> T prepareRequest(T request) {
    request.setSuspendPolicy(Registry.is("debugger.watch.return.speedup") ? EventRequest.SUSPEND_EVENT_THREAD : EventRequest.SUSPEND_NONE);
    if (myThread != null) {
      if (request instanceof MethodEntryRequest) {
        ((MethodEntryRequest)request).addThreadFilter(myThread);
      }
      else if (request instanceof MethodExitRequest) {
        ((MethodExitRequest)request).addThreadFilter(myThread);
      }
    }
    request.putProperty(WATCHER_REQUEST_KEY, true);
    return request;
  }

  public boolean processEvent(Event event) {
    EventRequest request = event.request();
    if (request == null || request.getProperty(WATCHER_REQUEST_KEY) == null) {
      return false;
    }

    if (event instanceof MethodEntryEvent) {
      processMethodEntryEvent(((MethodEntryEvent)event));
    }
    else if (event instanceof MethodExitEvent) {
      processMethodExitEvent(((MethodExitEvent)event));
    }
    return true;
  }

  @Override
  public void customizeRenderer(SimpleColoredComponent renderer) {
    renderer.setIcon(AllIcons.Debugger.WatchLastReturnValue);
    renderer.append(DebuggerBundle.message("action.watches.method.return.value.enable"));
  }
}
