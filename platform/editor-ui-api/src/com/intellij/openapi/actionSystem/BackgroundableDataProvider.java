// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BackgroundableDataProvider extends DataProvider {

  /**
   * Called on UI thread, should be fast: just get the information from Swing components needed to create the actual asynchronous data provider.
   * @return a data provider that might be called in a background thread (so shouldn't access any Swing hierarchy).
   */
  @Nullable
  DataProvider createBackgroundDataProvider();

  @Override
  default @Nullable Object getData(@NotNull String dataId) {
    DataProvider async = createBackgroundDataProvider();
    return async == null ? null : async.getData(dataId);
  }
}
