// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.managerThread;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface DebuggerManagerThread {
  /**
   * executes command in DebuggerManagerThread
   */
  void invokeCommand(DebuggerCommand command);
}
