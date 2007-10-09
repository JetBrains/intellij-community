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
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public abstract class ExecutionManager {
  public static ExecutionManager getInstance(final Project project) {
    return project.getComponent(ExecutionManager.class);
  }

  public abstract RunContentManager getContentManager();

  public abstract void compileAndRun(Runnable startRunnable, RunProfile configuration, RunProfileState state);

  /**
   * Executes arbitrary java vm executable and attaches run console window to it.
   *
   * @param cmdLine configure jre, classpath, vm parameters, working dir etc.
   * @param contentName name of the run content tab
   * @param dataContext usually passed to AnAction.actionPerformed(). Required for correct run tabs replacement management.
   * @throws ExecutionException
   */
  public abstract void execute(JavaParameters cmdLine, String contentName, DataContext dataContext) throws ExecutionException;

  /**
   * Same as {@link #execute(com.intellij.execution.configurations.JavaParameters, String, com.intellij.openapi.actionSystem.DataContext)}
   * but also takes filters to apply to console created.
   * @param cmdLine configure jre, classpath, vm parameters, working dir etc.
   * @param contentName name of the run content tab
   * @param dataContext usually passed to AnAction.actionPerformed(). Required for correct run tabs replacement management.
   * @param filters to apply to console view created.
   * @throws ExecutionException
   */
  public abstract void execute(JavaParameters cmdLine, String contentName, DataContext dataContext, Filter[] filters) throws ExecutionException;

  public abstract ProcessHandler[] getRunningProcesses();
}
