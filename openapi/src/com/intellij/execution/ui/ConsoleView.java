/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;

public interface ConsoleView extends ExecutionConsole{
  void print(String s, ConsoleViewContentType contentType);
  void clear();
  void scrollTo(int offset);

  void attachToProcess(ProcessHandler processHandler);

  void setOutputPaused(boolean value);
  boolean isOutputPaused();

  boolean hasDeferredOutput();

  void performWhenNoDeferredOutput(Runnable runnable);

  void setHelpId(String helpId);

  void addMessageFilter(Filter filter);

  void printHyperlink(String hyperlinkText, HyperlinkInfo info);

  int getContentSize();

  boolean canPause();
}