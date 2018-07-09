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
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class SimpleStackFrameContext implements StackFrameContext{
  private final StackFrameProxy myProxy;
  private final DebugProcess myProcess;

  public SimpleStackFrameContext(StackFrameProxy proxy, DebugProcess process) {
    myProxy = proxy;
    myProcess = process;
  }

  @Override
  public StackFrameProxy getFrameProxy() {
    return myProxy;
  }

  @Override
  @NotNull
  public DebugProcess getDebugProcess() {
    return myProcess;
  }
}
