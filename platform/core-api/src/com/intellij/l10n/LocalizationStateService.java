// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public interface LocalizationStateService {
  static @Nullable LocalizationStateService getInstance() {
    if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        return app.getService(LocalizationStateService.class);
      }
    }
    return null;
  }

  @NotNull String getSelectedLocale();
  void setSelectedLocale(@NotNull String locale);
  void setSelectedLocale(@NotNull String locale, Boolean ignoreRestart);
  @NotNull String getLastSelectedLocale();
  Boolean isRestartRequired();
  void resetLocaleIfNeeded();
}
