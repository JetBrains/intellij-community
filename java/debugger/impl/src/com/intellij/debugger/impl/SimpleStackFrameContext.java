// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class SimpleStackFrameContext implements StackFrameContext {
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
  public @NotNull DebugProcess getDebugProcess() {
    return myProcess;
  }
}
