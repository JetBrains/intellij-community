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