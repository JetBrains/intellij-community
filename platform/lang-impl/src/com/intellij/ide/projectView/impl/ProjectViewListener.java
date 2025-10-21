// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProjectViewListener {

  @Topic.ProjectLevel
  Topic<ProjectViewListener> TOPIC = new Topic<>(ProjectViewListener.class);

  /**
   * @param current  a pane that is currently shown
   * @param previous a pane that was previously shown or {@code null} for the first call
   */
  default void paneShown(@NotNull AbstractProjectViewPane current, @Nullable AbstractProjectViewPane previous) {
  }

  /**
   * Invoked synchronously every time {@link AbstractProjectViewPane#updateFromRoot(boolean)} is called.
   * <p>
   *   At the moment only supported for {@link AbstractProjectViewPaneWithAsyncSupport}.
   * </p>
   * @param pane the pane being updated
   */
  @ApiStatus.Experimental
  default void paneUpdateScheduled(@NotNull AbstractProjectViewPane pane) { }

  default void initStarted() { }
  default void initCachedNodesLoaded() { }
  default void initCompleted() { }
}
