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
package com.intellij.debugger.engine;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.sun.jdi.ThreadReference;

public class DebugProcessAdapter implements DebugProcessListener{
  //executed in manager thread
  public void connectorIsReady() {
  }

  //executed in manager thread
  public void paused(SuspendContext suspendContext) {

  }

  //executed in manager thread
  public void resumed(SuspendContext suspendContext) {

  }

  //executed in manager thread
  public void processDetached(DebugProcess process, boolean closedByUser) {

  }

  //executed in manager thread
  public void processAttached(DebugProcess process) {

  }

  //executed in manager thread
  public void threadStarted(DebugProcess proc, ThreadReference thread) {
  }

  //executed in manager thread
  public void threadStopped(DebugProcess proc, ThreadReference thread) {
  }

  public void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection) {

  }
}
