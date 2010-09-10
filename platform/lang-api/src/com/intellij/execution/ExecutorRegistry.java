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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class ExecutorRegistry implements ApplicationComponent {

  public static ExecutorRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ExecutorRegistry.class);
  }

  @NotNull
  public abstract Executor[] getRegisteredExecutors();

  public abstract Executor getExecutorById(final String executorId);

  public abstract boolean isStarting(Project project, String executorId, String runnerId);
}
