/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

import java.util.EventListener;

public interface ProcessListener extends EventListener {
  void startNotified(ProcessEvent event);

  void processTerminated(ProcessEvent event);

  void processWillTerminate(ProcessEvent event, boolean willBeDestroyed);

  void onTextAvailable(ProcessEvent event, Key outputType);
}