// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class MultiRoutingWatchServiceDelegate implements WatchService {
  final @NotNull WatchService myDelegate;
  private final @NotNull MultiRoutingFileSystemProvider myProvider;
  private final @NotNull FileSystem myLocalFs;
  private final @NotNull Map<WatchKey, MultiRoutingWatchKeyDelegate> myWrappedKeys = new IdentityHashMap<>();
  private final @NotNull LinkedBlockingQueue<WatchKey> myEventQueue = new LinkedBlockingQueue<>();
  private final @NotNull ConcurrentHashMap<FileSystem, WatchService> myBackendServices = new ConcurrentHashMap<>();
  private volatile boolean myClosed = false;

  MultiRoutingWatchServiceDelegate(@NotNull WatchService delegate,
                                   @NotNull MultiRoutingFileSystemProvider provider,
                                   @NotNull FileSystem localFs) {
    myDelegate = delegate;
    myProvider = provider;
    myLocalFs = localFs;
    startDrainThread(delegate, "local");
  }

  /**
   * Returns the backend {@link WatchService} responsible for the given delegate file system.
   * For the local file system, the pre-created delegate is returned.
   * For other file systems a new WatchService is lazily created and a drain thread is started.
   */
  @NotNull WatchService getBackendWatchService(@NotNull FileSystem delegatePathFs) throws IOException {
    if (delegatePathFs == myLocalFs) return myDelegate;
    try {
      return myBackendServices.computeIfAbsent(delegatePathFs, fs -> {
        try {
          WatchService ws = fs.newWatchService();
          startDrainThread(ws, fs.getClass().getSimpleName());
          return ws;
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private void startDrainThread(@NotNull WatchService ws, @NotNull String name) {
    Thread t = new Thread(() -> {
      try {
        while (!myClosed) {
          myEventQueue.put(ws.take());
        }
      }
      catch (InterruptedException | ClosedWatchServiceException ignored) {
        // both signal shutdown
      }
    }, "MRFS-drain-" + name);
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void close() throws IOException {
    myClosed = true;
    try {
      myDelegate.close();
    }
    finally {
      for (WatchService ws : myBackendServices.values()) {
        try {
          ws.close();
        }
        catch (IOException ignored) {
        }
      }
      synchronized (myWrappedKeys) {
        myWrappedKeys.clear();
      }
      myEventQueue.add(CLOSE_SENTINEL);
    }
  }

  @Override
  public WatchKey poll() {
    if (myClosed) throw new ClosedWatchServiceException();
    WatchKey key = myEventQueue.poll();
    if (key == null) return null;
    if (key == CLOSE_SENTINEL || myClosed) {
      myEventQueue.add(CLOSE_SENTINEL);
      throw new ClosedWatchServiceException();
    }
    return wrapDelegateKey(key);
  }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
    if (myClosed) throw new ClosedWatchServiceException();
    WatchKey key = myEventQueue.poll(timeout, unit);
    if (key == null) return null;
    if (key == CLOSE_SENTINEL || myClosed) {
      myEventQueue.add(CLOSE_SENTINEL);
      throw new ClosedWatchServiceException();
    }
    return wrapDelegateKey(key);
  }

  @Override
  public WatchKey take() throws InterruptedException {
    if (myClosed) throw new ClosedWatchServiceException();
    WatchKey key = myEventQueue.take();
    if (key == CLOSE_SENTINEL || myClosed) {
      myEventQueue.add(CLOSE_SENTINEL);
      throw new ClosedWatchServiceException();
    }
    return wrapDelegateKey(key);
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

  private static final WatchKey CLOSE_SENTINEL = new WatchKey() {
    @Override public boolean isValid() { return false; }
    @Override public List<WatchEvent<?>> pollEvents() { return java.util.List.of(); }
    @Override public boolean reset() { return false; }
    @Override public void cancel() { }
    @Override public Watchable watchable() { throw new UnsupportedOperationException(); }
  };
}
