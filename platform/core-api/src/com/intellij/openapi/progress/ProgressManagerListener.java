// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ProgressManagerListener {

  @Topic.AppLevel
  Topic<ProgressManagerListener> TOPIC = new Topic<>(ProgressManagerListener.class,
                                                     Topic.BroadcastDirection.NONE,
                                                     true);

  default void beforeTaskStart(@NotNull Task task,
                               @NotNull ProgressIndicator indicator) {}

  default void afterTaskStart(@NotNull Task task,
                              @NotNull ProgressIndicator indicator) {}

  default void beforeTaskFinished(@NotNull Task task) {}

  default void afterTaskFinished(@NotNull Task task) {}
}
