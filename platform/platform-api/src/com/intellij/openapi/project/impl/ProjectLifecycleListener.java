// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Reports some project lifecycle events. Note that these events are published on application-level {@link com.intellij.util.messages.MessageBus}.
 * They're also delivered for subscribers on project and module levels, but they will need to check that the events are relevant, i.e. the
 * {@code project} parameter is the project those subscribers are associated with.
 */
public interface ProjectLifecycleListener {
  Topic<ProjectLifecycleListener> TOPIC = Topic.create("Various stages of project lifecycle notifications", ProjectLifecycleListener.class);

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  default void projectComponentsInitialized(@NotNull Project project) {
  }

  default void beforeProjectLoaded(@NotNull Project project) {
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.project.ProjectManagerListener#projectClosed(Project)}
   */
  @Deprecated
  default void afterProjectClosed(@SuppressWarnings("unused") @NotNull Project project) {
  }
}
