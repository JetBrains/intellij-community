// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events;

import com.intellij.debugger.impl.DebuggerTaskImpl;
import org.jetbrains.annotations.Async;

/**
 * @author lex
 */
public abstract class DebuggerCommandImpl extends DebuggerTaskImpl {
  private final Priority myPriority;

  protected abstract void action() throws Exception;

  protected void commandCancelled() {
  }

  public DebuggerCommandImpl() {
    this(Priority.LOW);
  }

  public DebuggerCommandImpl(Priority priority) {
    myPriority = priority;
  }

  @Override
  public Priority getPriority() {
    return myPriority;
  }

  public final void notifyCancelled() {
    try {
      commandCancelled();
    }
    finally {
      release();
    }
  }

  @Async.Execute
  public final void run() throws Exception{
    try {
      action();
    }
    finally {
      release();
    }
  }
}
