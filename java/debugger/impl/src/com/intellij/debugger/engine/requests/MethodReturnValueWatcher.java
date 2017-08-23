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
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.ArrayUtil;
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

import java.lang.reflect.InvocationTargetException;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 23, 2006
 */
public class MethodReturnValueWatcher implements OverheadProducer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.requests.MethodReturnValueWatcher");
  private @Nullable Method myLastExecutedMethod;
  private @Nullable Value myLastMethodReturnValue;

  private ThreadReference myThread;
  private @Nullable MethodEntryRequest myEntryRequest;
  private @Nullable Method myEntryMethod;
  private @Nullable MethodExitRequest myExitRequest;

  private java.lang.reflect.Method myReturnValueMethod;
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
      //myLastMethodReturnValue = event.returnValue();
      try {
        if (myReturnValueMethod == null) {
          //noinspection HardCodedStringLiteral
          myReturnValueMethod = MethodExitEvent.class.getDeclaredMethod("returnValue", ArrayUtil.EMPTY_CLASS_ARRAY);
        }
        final Value retVal = (Value)myReturnValueMethod.invoke(event);
        
        if (method == null || !"void".equals(method.returnTypeName())) {
          // remember methods with non-void return types only
          myLastExecutedMethod = method;
          myLastMethodReturnValue = retVal;
        }
      }
      catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ignored) {
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
  public void customizeRenderer(ColoredTableCellRenderer renderer) {
    renderer.setIcon(AllIcons.Debugger.WatchLastReturnValue);
    renderer.append(DebuggerBundle.message("action.watches.method.return.value.enable"));
  }
}
