// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleColoredComponent;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
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
public class MethodReturnValueWatcher {
  private static final Logger LOG = Logger.getInstance(MethodReturnValueWatcher.class);
  private @Nullable Method myLastExecutedMethod;
  private @Nullable Value myLastMethodReturnValue;

  private ThreadReference myThread;
  private @Nullable MethodEntryRequest myEntryRequest;
  private @Nullable Method myEntryMethod;
  private @Nullable MethodExitRequest myExitRequest;

  private volatile boolean myTrackingEnabled;
  private final EventRequestManager myRequestManager;
  private final DebugProcessImpl myProcess;
  private final Overhead myOverhead;

  public MethodReturnValueWatcher(EventRequestManager requestManager, DebugProcessImpl process) {
    myRequestManager = requestManager;
    myProcess = process;
    myOverhead = new Overhead(process);
  }

  private void processMethodExitEvent(MethodExitEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("<- " + event.method());
    }
    try {
      if (myEntryMethod != null) {
        // first check declaring type to avoid method calculation in some cases
        if (myEntryMethod.declaringType().equals(event.location().declaringType()) && myEntryMethod.equals(event.method())) {
          LOG.debug("Now watching all");
          enableEntryWatching(true);
          myEntryMethod = null;
          DebuggerUtilsAsync.setEnabled(createExitRequest(), true);
        }
        else {
          return;
        }
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
      if (myEntryRequest != null && myEntryRequest.isEnabled() && myEntryMethod == null) {
        myExitRequest = createExitRequest();
        myExitRequest.addClassFilter(event.location().declaringType());
        myEntryMethod = event.method();
        DebuggerUtilsAsync.setEnabled(myExitRequest, true);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Now watching only " + event.method());
        }

        enableEntryWatching(false);
      }
    }
    catch (Exception e) {
      DebuggerUtilsImpl.logError(e);
    }
  }

  private void enableEntryWatching(boolean enable) {
    if (myEntryRequest != null) {
      DebuggerUtilsAsync.setEnabled(myEntryRequest, enable);
    }
  }

  public @Nullable Method getLastExecutedMethod() {
    return myLastExecutedMethod;
  }

  public @Nullable Value getLastMethodReturnValue() {
    return myLastMethodReturnValue;
  }

  private static boolean isEnabled() {
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

  private void updateRequestState(final boolean enabled, final @Nullable ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (myEntryRequest != null) {
        DebuggerUtilsAsync.deleteEventRequest(myRequestManager, myEntryRequest);
        myEntryRequest = null;
      }
      if (myExitRequest != null) {
        DebuggerUtilsAsync.deleteEventRequest(myRequestManager, myExitRequest);
        myExitRequest = null;
      }
      if (enabled) {
        OverheadTimings.add(myProcess, myOverhead, 1, null);
        clear();
        myThread = thread;

        DebuggerUtilsAsync.setEnabled(createEntryRequest(), true);
        DebuggerUtilsAsync.setEnabled(createExitRequest(), true);
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

  private @NotNull MethodExitRequest createExitRequest() {
    DebuggerManagerThreadImpl.assertIsManagerThread(); // to ensure EventRequestManager synchronization
    if (myExitRequest != null) {
      DebuggerUtilsAsync.deleteEventRequest(myRequestManager, myExitRequest);
    }
    myExitRequest = prepareRequest(myRequestManager.createMethodExitRequest());
    return myExitRequest;
  }

  private @NotNull <T extends EventRequest> T prepareRequest(T request) {
    request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
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

  static class Overhead implements OverheadProducer {
    private final DebugProcessImpl myProcess;

    Overhead(DebugProcessImpl process) {
      myProcess = process;
    }

    @Override
    public boolean isEnabled() {
      return MethodReturnValueWatcher.isEnabled();
    }

    @Override
    public void setEnabled(final boolean enabled) {
      myProcess.setWatchMethodReturnValuesEnabled(enabled);
    }

    @Override
    public void customizeRenderer(SimpleColoredComponent renderer) {
      renderer.setIcon(AllIcons.Debugger.WatchLastReturnValue);
      renderer.append(JavaDebuggerBundle.message("action.watches.method.return.value.enable"));
    }
  }
}
