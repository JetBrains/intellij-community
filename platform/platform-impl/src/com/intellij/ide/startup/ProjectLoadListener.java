// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ProjectLoadListener {
  @Topic.AppLevel
  Topic<ProjectLoadListener> TOPIC = new Topic<>(ProjectLoadListener.class, Topic.BroadcastDirection.NONE);

  default void postStartUpActivitiesPassed() {
  }

  default void dumbUnawarePostStartUpActivitiesPassed() {
  }
}
