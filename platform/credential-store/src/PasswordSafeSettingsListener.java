package com.intellij.credentialStore;

import org.jetbrains.annotations.NotNull;

public interface PasswordSafeSettingsListener {
  default void typeChanged(@NotNull PasswordSafeSettings.ProviderType oldValue, @NotNull PasswordSafeSettings.ProviderType newValue) {
  }

  default void credentialStoreCleared() {
  }
}