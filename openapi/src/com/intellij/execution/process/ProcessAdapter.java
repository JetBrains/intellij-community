/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

public abstract class ProcessAdapter implements ProcessListener{
  public void startNotified(final ProcessEvent event) {
  }

  public void processTerminated(final ProcessEvent event) {
  }

  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
  }

  public void onTextAvailable(final ProcessEvent event, final Key outputType) {
  }
}