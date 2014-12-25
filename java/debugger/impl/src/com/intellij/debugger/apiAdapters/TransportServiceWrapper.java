/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.connect.spi.TransportService;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author max
 */
public class TransportServiceWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.apiAdapters.TransportService");

  private final TransportService myTransport;
  private final Class<? extends TransportService> myDelegateClass;

  private static final String SOCKET_TRANSPORT_CLASS = "com.sun.tools.jdi.SocketTransportService";
  private static final String SHMEM_TRANSPORT_CLASS = "com.sun.tools.jdi.SharedMemoryTransportService";
  
  private TransportServiceWrapper(Class<? extends TransportService> delegateClass) throws NoSuchMethodException,
                                                      IllegalAccessException,
                                                      InvocationTargetException,
                                                      InstantiationException {
    myDelegateClass = delegateClass;
    final Constructor constructor = delegateClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    constructor.setAccessible(true);
    myTransport = (TransportService)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public TransportService.ListenKey startListening() throws IOException {
    return myTransport.startListening();
  }

  public TransportService.ListenKey startListening(String address) throws IOException {
    return myTransport.startListening(address);
  }

  public void stopListening(final TransportService.ListenKey address) throws IOException {
    myTransport.stopListening(address);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String transportId() {
    if (SOCKET_TRANSPORT_CLASS.equals(myDelegateClass.getName())) {
      return "dt_socket";
    }
    if (SHMEM_TRANSPORT_CLASS.equals(myDelegateClass.getName())) {
      return "dt_shmem";
    }
    LOG.error("Unknown service");
    return "<unknown>";
  }

  public static TransportServiceWrapper getTransportService(boolean forceSocketTransport) throws ExecutionException {
    TransportServiceWrapper transport;
    try {
      try {
        if (forceSocketTransport) {
          transport = new TransportServiceWrapper((Class<? extends TransportService>)Class.forName(SOCKET_TRANSPORT_CLASS));
        }
        else {
          transport = new TransportServiceWrapper((Class<? extends TransportService>)Class.forName(SHMEM_TRANSPORT_CLASS));
        }
      }
      catch (UnsatisfiedLinkError ignored) {
        transport = new TransportServiceWrapper((Class<? extends TransportService>)Class.forName(SOCKET_TRANSPORT_CLASS));
      }
    }
    catch (Exception e) {
      throw new ExecutionException(e.getClass().getName() + " : " + e.getMessage());
    }
    return transport;
  }

}
