/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * @author ven
 */
class ProcessProxyImpl implements ProcessProxy {
  public static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");

  public static final String PROPERTY_BIN_PATH = "idea.launcher.bin.path";
  public static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  public static final String LAUNCH_MAIN_CLASS = "com.intellij.rt.execution.application.AppMain";

  private final ServerSocket mySocket;
  private Writer myWriter;

  public ProcessProxyImpl() throws IOException {
    mySocket = new ServerSocket();
    mySocket.bind(new InetSocketAddress("127.0.0.1", 0));
    mySocket.setSoTimeout(10000);
  }

  @Override
  public int getPortNumber() {
    return mySocket.getLocalPort();
  }

  @Override
  public synchronized void attach(@NotNull ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      myWriter = new OutputStreamWriter(mySocket.accept().getOutputStream(), "US-ASCII");
    }
    catch (IOException e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }

  private synchronized void writeLine(String s) {
    try {
      myWriter.write(s);
      myWriter.write('\n');
      myWriter.flush();
    }
    catch (IOException e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }

  @Override
  public synchronized boolean canSendBreak() {
    if (myWriter == null) return false;
    String libName = null;
    if (SystemInfo.isWindows) libName = "breakgen.dll";
    else if (SystemInfo.isMac) libName = "libbreakgen.jnilib";
    else if (SystemInfo.isLinux) libName = "libbreakgen.so";
    return libName != null && new File(PathManager.getBinPath(), libName).exists();
  }

  @Override
  public synchronized boolean canSendStop() {
    return myWriter != null;
  }

  @Override
  public void sendBreak() {
    writeLine("BREAK");
  }

  @Override
  public void sendStop() {
    writeLine("STOP");
  }

  @Override
  public synchronized void destroy() {
    try {
      if (myWriter != null) {
        myWriter.close();
      }
      mySocket.close();
    }
    catch (IOException e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }
}