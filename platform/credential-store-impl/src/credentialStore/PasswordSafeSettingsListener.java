// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface PasswordSafeSettingsListener {
  @Topic.AppLevel
  Topic<PasswordSafeSettingsListener> TOPIC = new Topic<>("PasswordSafeSettingsListener", PasswordSafeSettingsListener.class);

  default void typeChanged(@NotNull ProviderType oldValue, @NotNull ProviderType newValue) {
  }

  default void credentialStoreCleared() {
  }
}
