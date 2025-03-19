// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.debugger;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides an ability to preconfigure task execution by an external system and to attach them with debugger.
 * <p>
 * The result of {@link #initializationCode(Project, String, String)} can be environment-based and always attached to the execution,
 * or runtime-parameter based.
 * If the result of {@link #isAlwaysAttached()} is true, the behavior of {@link #initializationCode(Project, String, String)}
 * is expected to be idempotent, because all the configuration should be done through the execution environment via the values, provided
 * by {@link #executionEnvironmentVariables(Project, String)}
 */
public interface DebuggerBackendExtension {
  ExtensionPointName<DebuggerBackendExtension> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.debuggerBackend");
  Key<String> RUNTIME_MODULE_DIR_KEY  = Key.create("RUNTIME_MODULE_DIR_KEY");

  String id();

  /**
   * @deprecated this is a Gradle specific method.
   * Use {@link org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension#configureTasks} instead.
   */
  @Deprecated
  default List<String> initializationCode(@Nullable Project project, @Nullable String dispatchPort, @NotNull String parameters) {
    return new ArrayList<>();
  }

  /**
   * @deprecated this is a Gradle specific method.
   * Use {@link org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension#configureTasks} instead.
   */
  @Deprecated
  default @NotNull Map<String, String> executionEnvironmentVariables(@Nullable Project project,
                                                                     @Nullable String dispatchPort,
                                                                     @NotNull String parameters) {
    return Map.of();
  }

  /**
   * @deprecated this is a Gradle specific method.
   * Use {@link org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension#configureTasks} instead.
   */
  @Deprecated
  default boolean isAlwaysAttached() {
    return false;
  }

  RunnerAndConfigurationSettings debugConfigurationSettings(@NotNull Project project,
                                                            @NotNull String processName,
                                                            @NotNull String processParameters);

  default HashMap<String, String> splitParameters(@NotNull String processParameters) {
    return ForkedDebuggerHelper.splitParameters(processParameters);
  }
}