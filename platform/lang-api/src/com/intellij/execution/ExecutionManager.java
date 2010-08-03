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
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public abstract class ExecutionManager {
  public static final Topic<ExecutionListener> EXECUTION_TOPIC = new Topic<ExecutionListener>("configuration executed", ExecutionListener.class,
                                                                                              Topic.BroadcastDirection.TO_PARENT);

  public static ExecutionManager getInstance(final Project project) {
    return project.getComponent(ExecutionManager.class);
  }

  public abstract RunContentManager getContentManager();

  public abstract void compileAndRun(Runnable startRunnable, RunProfile configuration, RunProfileState state);

  public abstract ProcessHandler[] getRunningProcesses();

  public abstract void startRunProfile(@NotNull RunProfileStarter starter, @NotNull RunProfileState state,
                              @NotNull Project project, @NotNull Executor executor, @NotNull ExecutionEnvironment env);
}
