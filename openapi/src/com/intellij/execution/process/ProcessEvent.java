/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import java.util.EventObject;

public class ProcessEvent extends EventObject{
  private String myText;
  private int myExitCode;

  public ProcessEvent(final ProcessHandler source) {
    super(source);
  }

  public ProcessEvent(final ProcessHandler source, final String text) {
    super(source);
    myText = text;
  }

  public ProcessEvent(final ProcessHandler source, final int exitCode) {
    super(source);
    myExitCode = exitCode;
  }

  public ProcessHandler getProcessHandler() {
    return (ProcessHandler)getSource();
  }

  public String getText() {
    return myText;
  }

  public int getExitCode() {
    return myExitCode;
  }
}