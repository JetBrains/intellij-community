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
package com.intellij.debugger.apiAdapters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author max
 */
public class ConnectionServiceWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.apiAdapters.ConnectionService");

  private static Class myDelegateClass;
  private final Object myConnection;

  static {
    try {
      //noinspection HardCodedStringLiteral
      myDelegateClass = SystemInfo.JAVA_VERSION.startsWith("1.4")
                        ? Class.forName("com.sun.tools.jdi.ConnectionService")
                        : Class.forName("com.sun.jdi.connect.spi.Connection");
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  public ConnectionServiceWrapper(final Object connection) {
    myConnection = connection;
  }

  public void close() throws IOException {
    try {
      //noinspection HardCodedStringLiteral
      final Method method = myDelegateClass.getMethod("close", ArrayUtil.EMPTY_CLASS_ARRAY);
      method.invoke(myConnection, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      LOG.error(e);
    }
  }

  public VirtualMachine createVirtualMachine() throws IOException {
    try {
      final VirtualMachineManager virtualMachineManager = Bootstrap.virtualMachineManager();
      //noinspection HardCodedStringLiteral
      final Method method = virtualMachineManager.getClass().getMethod("createVirtualMachine", new Class[]{myDelegateClass});
      return (VirtualMachine)method.invoke(virtualMachineManager, new Object[]{myConnection});
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      if (cause instanceof VMDisconnectedException) {
        return null; // ignore this one
      }
      LOG.error(e);
    }
    return null;
  }
}
