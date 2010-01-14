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
package com.intellij.debugger.engine.events;

import com.intellij.debugger.impl.DebuggerTaskImpl;

/**
 * @author lex
 */
public abstract class DebuggerCommandImpl extends DebuggerTaskImpl {
  protected abstract void action() throws Exception;

  protected void commandCancelled() {
  }

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

  public final void run() throws Exception{
    try {
      action();
    }
    finally {
      release();
    }
  }
}
