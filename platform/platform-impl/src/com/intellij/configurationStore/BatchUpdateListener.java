// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.util.messages.Topic;

/**
 * Only for project- and module- level components.
 */
public interface BatchUpdateListener {
  @Topic.ProjectLevel
  Topic<BatchUpdateListener> TOPIC = new Topic<>(BatchUpdateListener.class, Topic.BroadcastDirection.NONE, true);

  default void onBatchUpdateStarted() {
  }

  default void onBatchUpdateFinished() {
  }
}
