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
import com.sun.jdi.Value;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.MethodExitRequest;
import org.jetbrains.annotations.NotNull;
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
  private @NotNull final MethodExitRequest myWatchMethodReturnValueRequest;
  private java.lang.reflect.Method myReturnValueMethod;
  private volatile boolean myIsTrackingEnabled;
  private boolean myFeatureEnabled;

  public MethodReturnValueWatcher(final MethodExitRequest request) {
    myWatchMethodReturnValueRequest = request;
    myIsTrackingEnabled = request.isEnabled();
    myFeatureEnabled = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
  }

  public boolean processMethodExitEvent(MethodExitEvent event) {
    if (event.request() != myWatchMethodReturnValueRequest) {
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

  public boolean isTrackingEnabled() {
    return myIsTrackingEnabled;
  }

  public void setFeatureEnabled(final boolean featureEnabled) {
    myFeatureEnabled = featureEnabled;
    updateRequestState(featureEnabled && myIsTrackingEnabled);
    myLastExecutedMethod = null;
    myLastMethodReturnValue = null;
  }

  public void setTrackingEnabled(boolean trackingEnabled) {
    myIsTrackingEnabled = trackingEnabled;
    updateRequestState(trackingEnabled && myFeatureEnabled);
  }

  private void updateRequestState(final boolean enabled) {
    try {
      if (enabled) {
        myLastExecutedMethod = null;
        myLastMethodReturnValue = null;
        if (!myWatchMethodReturnValueRequest.isEnabled()) {
          myWatchMethodReturnValueRequest.enable();
        }
      }
      else {
        if (myWatchMethodReturnValueRequest.isEnabled()) {
          myWatchMethodReturnValueRequest.disable();
        }
      }
    }
    catch (ObjectCollectedException ignored) {
    }
  }
}
