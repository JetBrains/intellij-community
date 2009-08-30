package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.DebuggingRunnerData;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class GenericDebuggerRunnerSettings implements JDOMExternalizable, DebuggingRunnerData {
  public String DEBUG_PORT = "";
  public int TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
  public boolean LOCAL = true;

  public GenericDebuggerRunnerSettings() {
    try {
      DEBUG_PORT = DebuggerUtils.getInstance().findAvailableDebugAddress(DebuggerSettings.SOCKET_TRANSPORT == TRANSPORT);
    }
    catch (ExecutionException e) {
      DEBUG_PORT = "";
    }
  }

  public String getDebugPort() {
    return DEBUG_PORT;
  }

  public boolean isRemote() {
    return !LOCAL;
  }

  public void setLocal(boolean isLocal) {
    LOCAL = isLocal;
  }

  private void updateDefaultAddress() {
    boolean useDefaultPort = "".equals(DEBUG_PORT);

    if (getTransport() == DebuggerSettings.SOCKET_TRANSPORT) {
      try {
        Integer.parseInt(DEBUG_PORT);
      }
      catch (NumberFormatException e) {
        useDefaultPort = true;
      }
    }

    if (useDefaultPort) {
      try {
        DEBUG_PORT = DebuggerUtils.getInstance().findAvailableDebugAddress(getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
      }
      catch (ExecutionException e) {
        DEBUG_PORT = "";
      }
    }
  }

  public void setDebugPort(String port) {
    DEBUG_PORT = port;
    updateDefaultAddress();
  }

  public void setTransport(int transport) {
    if (getTransport() != transport) {
      setDebugPort("");
    }
    TRANSPORT = transport;
    updateDefaultAddress();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    updateDefaultAddress();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public int getTransport() {
    if (LOCAL) {
      return DebuggerSettings.getInstance().DEBUGGER_TRANSPORT;
    }
    else {
      return TRANSPORT;
    }
  }
}
