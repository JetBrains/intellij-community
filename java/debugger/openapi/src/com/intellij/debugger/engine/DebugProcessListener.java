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

import java.util.EventListener;


public interface DebugProcessListener extends EventListener{
  //executed in manager thread
  void connectorIsReady();

  //executed in manager thread
  void paused(SuspendContext suspendContext);

  //executed in manager thread
  void resumed(SuspendContext suspendContext);

  //executed in manager thread
  void processDetached(DebugProcess process, boolean closedByUser);

  //executed in manager thread
  void processAttached(DebugProcess process);

  void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection);
  
  void threadStarted(DebugProcess proc, ThreadReference thread);
  
  void threadStopped(DebugProcess proc, ThreadReference thread);
}

