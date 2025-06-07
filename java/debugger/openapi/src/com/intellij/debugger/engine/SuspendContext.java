// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface SuspendContext extends StackFrameContext {
  int getSuspendPolicy();

  @Nullable
  ThreadReferenceProxy getThread();
}
