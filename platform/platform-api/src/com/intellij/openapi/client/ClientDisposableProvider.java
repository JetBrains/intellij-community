// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface ClientDisposableProvider {
  static @Nullable Disposable getCurrentDisposable() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return null;
    }
    ClientDisposableProvider provider = application.getService(ClientDisposableProvider.class);
    return provider != null ? provider.getDisposable() : application;
  }

  @NotNull Disposable getDisposable();
}
