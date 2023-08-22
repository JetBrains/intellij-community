// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface PortableCachesLoadListener {
  @Topic.ProjectLevel
  Topic<PortableCachesLoadListener> TOPIC = new Topic<>(PortableCachesLoadListener.class, Topic.BroadcastDirection.NONE, true);

  default void loadingStarted() {}

  default void loadingFinished(boolean isSuccessfully) {}
}
