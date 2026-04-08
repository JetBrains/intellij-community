// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class MultiRoutingWatchServiceDelegate implements WatchService {
  final @NotNull WatchService myDelegate;
  private final @NotNull MultiRoutingFileSystemProvider myProvider;
  private final @NotNull Map<WatchKey, MultiRoutingWatchKeyDelegate> myWrappedKeys = new IdentityHashMap<>();

  MultiRoutingWatchServiceDelegate(@NotNull WatchService delegate, @NotNull MultiRoutingFileSystemProvider provider) {
    myDelegate = delegate;
    myProvider = provider;
  }

  @Override
  public void close() throws IOException {
    try {
      myDelegate.close();
    }
    finally {
      synchronized (myWrappedKeys) {
        myWrappedKeys.clear();
      }
    }
  }

  @Override
  public WatchKey poll() {
    WatchKey watchKey = myDelegate.poll();
    if (watchKey == null) return null;
    return wrapDelegateKey(watchKey);
  }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
    WatchKey watchKey = myDelegate.poll(timeout, unit);
    if (watchKey == null) return null;
    return wrapDelegateKey(watchKey);
  }

  @Override
  public WatchKey take() throws InterruptedException {
    WatchKey watchKey = myDelegate.take();
    if (watchKey == null) return null;
    return wrapDelegateKey(watchKey);
  }

  @NotNull WatchKey wrapDelegateKey(@NotNull WatchKey watchKey) {
    // JDK watch services return the same key instance from register() and delivery. Preserve that
    // identity even though MRFS has to wrap backend keys before exposing them to callers.
    synchronized (myWrappedKeys) {
      MultiRoutingWatchKeyDelegate wrappedKey = myWrappedKeys.get(watchKey);
      if (wrappedKey == null) {
        wrappedKey = new MultiRoutingWatchKeyDelegate(watchKey, myProvider, this);
        myWrappedKeys.put(watchKey, wrappedKey);
      }
      return wrappedKey;
    }
  }

  void forgetDelegateKey(@NotNull WatchKey watchKey) {
    synchronized (myWrappedKeys) {
      myWrappedKeys.remove(watchKey);
    }
  }
}
