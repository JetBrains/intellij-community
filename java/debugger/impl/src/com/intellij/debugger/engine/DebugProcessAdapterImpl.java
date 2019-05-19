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

import org.jetbrains.annotations.NotNull;

/**
 * @author lex
 */
public class DebugProcessAdapterImpl implements DebugProcessListener {
  //executed in manager thread
  @Override
  public final void paused(@NotNull SuspendContext suspendContext) {
    paused(((SuspendContextImpl)suspendContext));
  }

  //executed in manager thread
  @Override
  public final void resumed(SuspendContext suspendContext) {
    resumed(((SuspendContextImpl)suspendContext));
  }

  //executed in manager thread
  @Override
  public final void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
    processDetached(((DebugProcessImpl)process), closedByUser);
  }

  //executed in manager thread
  @Override
  public final void processAttached(@NotNull DebugProcess process) {
    processAttached(((DebugProcessImpl)process));
  }

  public void paused(SuspendContextImpl suspendContext) {
  }

  //executed in manager thread
  public void resumed(SuspendContextImpl suspendContext) {
  }

  //executed in manager thread
  public void processDetached(DebugProcessImpl process, boolean closedByUser) {
  }

  //executed in manager thread
  public void processAttached(DebugProcessImpl process) {
  }
}
