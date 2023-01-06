// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.util.Pair;

import java.util.LinkedList;
import java.util.List;

public class MultiProcessCommand implements Runnable {
  private final List<Pair<DebugProcessImpl, DebuggerCommandImpl>> myCommands = new LinkedList<>();

  @Override
  public void run() {
    while (true) {
      Pair<DebugProcessImpl, DebuggerCommandImpl> pair;
      synchronized (myCommands) {
        if (myCommands.isEmpty()) {
          break;
        }
        pair = myCommands.remove(0);
      }
      pair.getFirst().getManagerThread().invokeAndWait(pair.getSecond());
    }
  }

  public void cancel() {
    synchronized (myCommands) {
      while (!myCommands.isEmpty()) {
        Pair<DebugProcessImpl, DebuggerCommandImpl> pair = myCommands.remove(0);
        pair.getSecond().notifyCancelled();
      }
    }
  }

  public boolean isEmpty() {
    return myCommands.isEmpty();
  }

  public void addCommand(DebugProcessImpl debugProcess, DebuggerCommandImpl command) {
    myCommands.add(Pair.create(debugProcess, command));
  }
}
