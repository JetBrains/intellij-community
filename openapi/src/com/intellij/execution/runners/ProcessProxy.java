/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;

public interface ProcessProxy {
  int getPortNumber();

  void attach(ProcessHandler processHandler);

  void sendBreak ();

  void sendStop ();
}