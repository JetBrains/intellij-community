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
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.openapi.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class MultiProcessCommand implements Runnable{
  private final List<Pair<DebugProcessImpl,  DebuggerCommandImpl>> myCommands = new ArrayList<Pair<DebugProcessImpl, DebuggerCommandImpl>>();

  public void run() {
    for(;;) {
      Pair<DebugProcessImpl,  DebuggerCommandImpl> pair;

      synchronized(myCommands) {
        if(myCommands.isEmpty()) break;

        pair = myCommands.get(0);
      }

      pair.getFirst().getManagerThread().invokeAndWait(pair.getSecond());

      synchronized(myCommands) {
        if(myCommands.isEmpty()) break;

        myCommands.remove(0);
      }
    }
  }

  public void terminate() {
    synchronized(myCommands) {
      if(myCommands.isEmpty()) return;
      Pair<DebugProcessImpl,  DebuggerCommandImpl> pair = myCommands.get(0);
      pair.getFirst().getManagerThread().terminateCommand(pair.getSecond());
      myCommands.clear();
    }
  }

  public void addCommand(DebugProcessImpl debugProcess, DebuggerCommandImpl command) {
    myCommands.add(new Pair<DebugProcessImpl, DebuggerCommandImpl>(debugProcess, command));
  }
}
