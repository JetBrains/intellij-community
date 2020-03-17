// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ProjectLoadListener {
  Topic<ProjectLoadListener> TOPIC = new Topic<>(ProjectLoadListener.class);

  default void postStartUpActivitiesPassed() {
  }

  default void dumbUnawarePostStartUpActivitiesPassed() {
  }
}
