// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class CommandOutputLogger extends ProcessAdapter {
  private final Logger myLogger;

  public CommandOutputLogger(Logger logger) {
    myLogger = logger;
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    String line =  event.getText();

    if (myLogger.isDebugEnabled()) {
      myLogger.debug(line);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      System.out.print(line);
    }
  }
}
