// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.service;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

public abstract class SharedThreadPool implements ExecutorService {
  public static SharedThreadPool getInstance() {
    return JpsServiceManager.getInstance().getService(SharedThreadPool.class);
  }

  public abstract @NotNull ExecutorService createBoundedExecutor(@NotNull @NonNls String name, int maxThreads);

  @ApiStatus.Internal
  protected SharedThreadPool() {
  }
}
