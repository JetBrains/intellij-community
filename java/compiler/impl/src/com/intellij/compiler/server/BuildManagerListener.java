// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 */
public interface BuildManagerListener {

  @Topic.AppLevel
  Topic<BuildManagerListener> TOPIC = new Topic<>(BuildManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  default void beforeBuildProcessStarted(@NotNull Project project, @NotNull UUID sessionId) {}

  default void buildStarted(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {}

  default void buildFinished(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {}
}
