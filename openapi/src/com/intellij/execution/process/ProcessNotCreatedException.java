/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;

public class ProcessNotCreatedException extends ExecutionException {
  private final GeneralCommandLine myCommandLine;

  public ProcessNotCreatedException(final String s, final GeneralCommandLine commandLine) {
    super(s);
    myCommandLine = commandLine;
  }

  public GeneralCommandLine getCommandLine() {
    return myCommandLine;
  }
}
