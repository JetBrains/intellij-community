/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  @Transient
  @Deprecated
  public String DEBUG_PORT = "";

  public int TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
  public boolean LOCAL = true;

  @Override
  @OptionTag("DEBUG_PORT")
  public String getDebugPort() {
    //noinspection deprecation
    return DEBUG_PORT;
  }

  @Override
  public void setDebugPort(String port) {
    //noinspection deprecation
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
    return LOCAL ? DebuggerSettings.getInstance().DEBUGGER_TRANSPORT : TRANSPORT;
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
