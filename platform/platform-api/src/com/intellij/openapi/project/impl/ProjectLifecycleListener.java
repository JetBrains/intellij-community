// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Reports some project lifecycle events.
 */
public interface ProjectLifecycleListener {
  @Topic.AppLevel
  Topic<ProjectLifecycleListener> TOPIC = new Topic<>(ProjectLifecycleListener.class, Topic.BroadcastDirection.NONE);

  /**
   * @deprecated Do not use.
   */
  @Deprecated(forRemoval = true)
  default void projectComponentsInitialized(@NotNull Project project) {
  }

  /**
   * @deprecated Deprecated for performance and stability reasons. Please find another solution.
   */
  @Deprecated(forRemoval = true)
  default void beforeProjectLoaded(@NotNull Project project) {
  }

  /**
   * @deprecated Deprecated for performance and stability reasons. Please find another solution.
   */
  @Deprecated(forRemoval = true)
  default void beforeProjectLoaded(@NotNull Path projectPath, @NotNull Project project) {
    beforeProjectLoaded(project);
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.project.ProjectManagerListener#projectClosed(Project)}
   */
  @Deprecated(forRemoval = true)
  default void afterProjectClosed(@NotNull Project project) {
  }
}
