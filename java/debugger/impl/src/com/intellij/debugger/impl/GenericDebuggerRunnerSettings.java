// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.configurations.DebuggingRunnerData;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.util.xmlb.SmartSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;

public class GenericDebuggerRunnerSettings implements DebuggingRunnerData, RunnerSettings {
  private final SmartSerializer mySerializer = new SmartSerializer();

  private String DEBUG_PORT = "";
  public int TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
  public boolean LOCAL = true;

  @Override
  @OptionTag("DEBUG_PORT")
  public String getDebugPort() {
    return DEBUG_PORT;
  }

  @Override
  public void setDebugPort(String port) {
    DEBUG_PORT = port;
  }

  @Override
  public boolean isRemote() {
    return !LOCAL;
  }

  @Override
  public void setLocal(boolean isLocal) {
    LOCAL = isLocal;
  }

  @Transient
  public int getTransport() {
    return LOCAL ? DebuggerSettings.getInstance().getTransport() : TRANSPORT;
  }

  public void setTransport(int transport) {
    TRANSPORT = transport;
  }

  @Override
  public void readExternal(Element element) {
    mySerializer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) {
    mySerializer.writeExternal(this, element);
  }
}
