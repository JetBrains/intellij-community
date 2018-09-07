// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.task;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 12/19/13
 *
 * @deprecated use {@link ExternalSystemTaskManager} interface
 */
@Deprecated
public abstract class AbstractExternalSystemTaskManager<S extends ExternalSystemExecutionSettings> implements ExternalSystemTaskManager<S> {

  @Override
  public abstract void executeTasks(@NotNull ExternalSystemTaskId id,
                                    @NotNull List<String> taskNames,
                                    @NotNull String projectPath,
                                    @Nullable S settings,
                                    @NotNull final List<String> vmOptions,
                                    @NotNull List<String> scriptParameters,
                                    @Nullable String jvmAgentSetup,
                                    @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException;
}
