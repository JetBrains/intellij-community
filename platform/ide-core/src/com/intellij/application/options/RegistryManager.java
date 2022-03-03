// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RegistryManager {
  @Topic.AppLevel
  @ApiStatus.Experimental
  @ApiStatus.Internal
  // only afterValueChanged is dispatched
  Topic<RegistryValueListener> TOPIC = new Topic<>(RegistryValueListener.class, Topic.BroadcastDirection.NONE, true);

  static @NotNull RegistryManager getInstance() {
    return ApplicationManager.getApplication().getService(RegistryManager.class);
  }

  boolean is(@NotNull String key);

  int intValue(@NotNull String key);

  @Nullable String stringValue(@NotNull String key);

  int intValue(@NotNull String key, int defaultValue);

  @NotNull
  RegistryValue get(@NotNull String key);
}
