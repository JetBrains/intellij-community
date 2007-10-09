/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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