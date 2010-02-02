/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RunContentManager {

  DataKey<RunContentDescriptor> RUN_CONTENT_DESCRIPTOR = DataKey.create("RUN_CONTENT_DESCRIPTOR");

  @Nullable
  RunContentDescriptor getSelectedContent();
  @Nullable
  RunContentDescriptor getSelectedContent(Executor runnerInfo);

  /**
   * to reduce number of open contents RunContentManager reuses
   * some of them during showRunContent (for ex. if a process was stopped) 
   * @return content that will be reused by showRunContent
   */
  @Nullable
  RunContentDescriptor getReuseContent(Executor requestor, @Nullable RunContentDescriptor contentToReuse);

  /**
   * @deprecated use {@link #getReuseContent(com.intellij.execution.Executor, RunContentDescriptor)}
   */
  @Deprecated
  @Nullable
  RunContentDescriptor getReuseContent(Executor requestor, DataContext dataContext);

  @Nullable
  RunContentDescriptor findContentDescriptor(Executor requestor, ProcessHandler handler);

  void showRunContent(@NotNull Executor executor, RunContentDescriptor descriptor, RunContentDescriptor contentToReuse);
  void showRunContent(@NotNull Executor executor, RunContentDescriptor descriptor);
  void hideRunContent(@NotNull Executor executor, RunContentDescriptor descriptor);
  boolean removeRunContent(@NotNull Executor executor, RunContentDescriptor descriptor);

  void toFrontRunContent(Executor requestor, RunContentDescriptor descriptor);
  void toFrontRunContent(Executor requestor, ProcessHandler handler);

  void addRunContentListener(RunContentListener listener);
  void removeRunContentListener(RunContentListener listener);

  void addRunContentListener(RunContentListener myContentListener, Executor executor);
}