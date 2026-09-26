// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Reports some project lifecycle events.
 */
public interface ProjectLifecycleListener {
  @Topic.AppLevel
  Topic<ProjectLifecycleListener> TOPIC = new Topic<>(ProjectLifecycleListener.class, Topic.BroadcastDirection.NONE);

  /**
   * @deprecated Use {@link com.intellij.openapi.project.ProjectManagerListener#projectClosed(Project)}
   */
  @Deprecated(forRemoval = true)
  default void afterProjectClosed(@NotNull Project project) {
  }
}
