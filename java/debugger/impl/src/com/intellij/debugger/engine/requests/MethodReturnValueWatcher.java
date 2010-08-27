/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 23, 2006
 */
public class MethodReturnValueWatcher  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.requests.MethodReturnValueWatcher");
  private @Nullable Method myLastExecutedMethod;
  private @Nullable Value myLastMethodReturnValue;
  private @Nullable MethodExitRequest myRequest;
  private java.lang.reflect.Method myReturnValueMethod;
  private volatile boolean myEnabled;
  private boolean myFeatureEnabled;
  private final EventRequestManager myRequestManager;

  public MethodReturnValueWatcher(EventRequestManager requestManager) {
    myRequestManager = requestManager;
    myFeatureEnabled = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
  }

  public boolean processMethodExitEvent(MethodExitEvent event) {
    if (event.request() != myRequest) {
      return false;
    }
    try {
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
      catch (NoSuchMethodException ignored) {
      }
      catch (IllegalAccessException ignored) {
      }
      catch (InvocationTargetException ignored) {
      }
    }
    catch (UnsupportedOperationException ex) {
      LOG.error(ex);
    }
    return true;
  }


  @Nullable
  public Method getLastExecutedMethod() {
    return myLastExecutedMethod;
  }

  @Nullable
  public Value getLastMethodReturnValue() {
    return myLastMethodReturnValue;
  }

  public boolean isFeatureEnabled() {
    return myFeatureEnabled;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setFeatureEnabled(final boolean featureEnabled) {
    myFeatureEnabled = featureEnabled;
    myLastExecutedMethod = null;
    myLastMethodReturnValue = null;
  }

  public void enable(ThreadReference thread) {
    setTrackingEnabled(true, thread);
  }
  
  public void disable() {
    setTrackingEnabled(false, null);
  }
  
  private void setTrackingEnabled(boolean trackingEnabled, final ThreadReference thread) {
    myEnabled = trackingEnabled;
    updateRequestState(trackingEnabled && myFeatureEnabled, thread);
  }

  private void updateRequestState(final boolean enabled, @Nullable final ThreadReference thread) {
    try {
      final MethodExitRequest request = myRequest;
      if (request != null) {
        myRequest = null;
        myRequestManager.deleteEventRequest(request);
      }
      if (enabled) {
        myLastExecutedMethod = null;
        myLastMethodReturnValue = null;
        myRequest = createRequest(thread);
        myRequest.enable();
      }
    }
    catch (ObjectCollectedException ignored) {
    }
  }
  
  private MethodExitRequest createRequest(@Nullable final ThreadReference thread) {
    final MethodExitRequest request = myRequestManager.createMethodExitRequest();
    request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
    if (thread != null) {
      request.addThreadFilter(thread);
    }
    return request;
  }
  
}
