/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.task;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public abstract class ProjectTaskRunner {

  public static final ExtensionPointName<ProjectTaskRunner> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskRunner");

  public abstract void run(@NotNull Project project,
                           @NotNull ProjectTaskContext context,
                           @Nullable ProjectTaskNotification callback,
                           @NotNull Collection<? extends ProjectTask> tasks);

  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull ProjectTask... tasks) {
    run(project, context, callback, Arrays.asList(tasks));
  }

  public abstract boolean canRun(@NotNull ProjectTask projectTask);

  public boolean canRun(@SuppressWarnings("unused") @NotNull Project project, @NotNull ProjectTask projectTask) {
    return canRun(projectTask);
  }

  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    return null;
  }

  /**
   * The flag indicates if the {@link ProjectTaskRunner} supports reporting an information about generated files during execution or not.
   * The fine-grained events per generated files allow greatly improve IDE performance for some activities like fast hotswap reload after incremental compilation.
   * <p/>
   * The support means responsibility to send {@link ProjectTaskContext#fileGenerated} events per each generated file
   * or at least supply effective output roots containing generated files using the {@link ProjectTaskContext#addDirtyOutputPathsProvider} method
   * if per-file events are not possible.
   *
   * @return true if the {@link ProjectTaskRunner} supports reporting an information about generated files during this runner tasks execution, false otherwise
   */
  @ApiStatus.Experimental
  public boolean isFileGeneratedEventsSupported() {
    return false;
  }
}
