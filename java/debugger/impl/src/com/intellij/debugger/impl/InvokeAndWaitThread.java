// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import org.jetbrains.annotations.NotNull;

public abstract class InvokeAndWaitThread<E extends DebuggerTask> extends InvokeThread<E> {

  public void invokeAndWait(final @NotNull E runnable) {
    runnable.hold();
    schedule(runnable);
    runnable.waitFor();
  }
}

