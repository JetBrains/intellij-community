/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;

public interface RunContentManager {
  RunContentDescriptor getSelectedContent();
  RunContentDescriptor getSelectedContent(RunnerInfo runnerInfo);

  /**
   * to reduce number of open contents RunContentManager reuses
   * some of them during showRunContent (for ex. if a process was stopped) 
   *
   * getReuseContent returns content that will be reused by showRunContent
   * @param dataContext
   * @return
   */
  RunContentDescriptor getReuseContent(JavaProgramRunner requestor, DataContext dataContext);

  void showRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor, RunContentDescriptor contentToReuse);
  void showRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor);
  void hideRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor);
  boolean removeRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor);

  void toFrontRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor);
  void toFrontRunContent(RunnerInfo requestor, ProcessHandler handler);

  void addRunContentListener(RunContentListener listener);
  void removeRunContentListener(RunContentListener listener);

  void addRunContentListener(RunContentListener myContentListener, RunnerInfo runnerInfo);
}