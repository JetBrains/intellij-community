package com.intellij.debugger.engine.managerThread;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DebuggerCommand {
  void action();

  void commandCancelled();
}
