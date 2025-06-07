// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

final class MultiRoutingWatchServiceDelegate implements WatchService {
  final @NotNull WatchService myDelegate;
  private final @NotNull MultiRoutingFileSystemProvider myProvider;

  MultiRoutingWatchServiceDelegate(@NotNull WatchService delegate, @NotNull MultiRoutingFileSystemProvider provider) {
    myDelegate = delegate;
    myProvider = provider;
  }

  @Override
  public void close() throws IOException {
    myDelegate.close();
  }

  @Override
  public WatchKey poll() {
    WatchKey watchKey = myDelegate.poll();
    if (watchKey == null) return null;
    return new MultiRoutingWatchKeyDelegate(watchKey, myProvider);
  }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
    WatchKey watchKey = myDelegate.poll(timeout, unit);
    if (watchKey == null) return null;
    return new MultiRoutingWatchKeyDelegate(watchKey, myProvider);
  }

  @Override
  public WatchKey take() throws InterruptedException {
    WatchKey watchKey = myDelegate.take();
    if (watchKey == null) return null;
    return new MultiRoutingWatchKeyDelegate(watchKey, myProvider);
  }
}
