package com.intellij.debugger.engine.managerThread;

import com.intellij.debugger.engine.SuspendContext;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DebuggerManagerThread {
  /**
   * executes command in DebuggerManagerThread
   *
   * @param command
   */
  public void invokeCommand       (DebuggerCommand command);
}
