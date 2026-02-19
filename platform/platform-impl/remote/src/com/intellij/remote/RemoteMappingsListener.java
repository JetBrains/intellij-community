// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface RemoteMappingsListener {

  @Topic.AppLevel
  Topic<RemoteMappingsListener> REMOTE_MAPPINGS_CHANGED = new Topic<>(RemoteMappingsListener.class, Topic.BroadcastDirection.NONE);

  void mappingsChanged(@NotNull String prefix, @NotNull String serverId);
  void mappingsChanged();
}
