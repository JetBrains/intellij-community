// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Reports some project lifecycle events. Note that these events are published on application-level {@link com.intellij.util.messages.MessageBus}.
 * They're also delivered for subscribers on project and module levels, but they will need to check that the events are relevant, i.e. the
 * {@code project} parameter is the project those subscribers are associated with.
 *
 * @author max
 */
public interface ProjectLifecycleListener {
  Topic<ProjectLifecycleListener> TOPIC = Topic.create("Various stages of project lifecycle notifications", ProjectLifecycleListener.class);

  default void projectComponentsRegistered(@NotNull Project project) {
  }

  default void projectComponentsInitialized(@NotNull Project project) {
  }

  default void beforeProjectLoaded(@NotNull Project project) {
  }

  default void afterProjectClosed(@NotNull Project project) {
  }

  /**
   * @deprecated Use {@link ProjectLifecycleListener}
   */
  @Deprecated
  abstract class Adapter implements ProjectLifecycleListener {
  }
}
