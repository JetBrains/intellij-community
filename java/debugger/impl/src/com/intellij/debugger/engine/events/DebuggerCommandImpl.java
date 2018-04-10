// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.events;

import com.intellij.debugger.impl.DebuggerTaskImpl;
import org.jetbrains.annotations.Async;

/**
 * @author lex
 */
public abstract class DebuggerCommandImpl extends DebuggerTaskImpl {
  protected abstract void action() throws Exception;

  protected void commandCancelled() {
  }

  @Override
  public Priority getPriority() {
    return Priority.LOW;
  }

  public final void notifyCancelled() {
    try {
      commandCancelled();
    }
    finally {
      release();
    }
  }

  @Async.Execute()
  public final void run() throws Exception{
    try {
      action();
    }
    finally {
      release();
    }
  }
}
