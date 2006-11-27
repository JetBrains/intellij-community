/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.requests;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Method;
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
  private @NotNull MethodExitRequest myWatchMethodReturnValueRequest;
  private java.lang.reflect.Method myReturnValueMethod;

  public MethodReturnValueWatcher(final MethodExitRequest request) {
    myWatchMethodReturnValueRequest = request;
  }

  public boolean processMethodExitEvent(MethodExitEvent event) {
    if (event.request() != myWatchMethodReturnValueRequest) {
      return false;
    }
    try {
      myLastExecutedMethod = event.method();
      //myLastMethodReturnValue = event.returnValue();
      try {
        if (myReturnValueMethod == null) {
          //noinspection HardCodedStringLiteral
          myReturnValueMethod = MethodExitEvent.class.getDeclaredMethod("returnValue", new Class[0]);
        }
        myLastMethodReturnValue = (Value)myReturnValueMethod.invoke(event);
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

  public void setTrackingEnabled(boolean enabled) {
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
}
