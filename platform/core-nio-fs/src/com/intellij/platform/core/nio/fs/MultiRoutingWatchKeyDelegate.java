// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

final class MultiRoutingWatchKeyDelegate implements WatchKey {
  private final @NotNull WatchKey myDelegate;
  private final @NotNull MultiRoutingFileSystemProvider myProvider;
  private final @NotNull MultiRoutingWatchServiceDelegate myOwner;

  MultiRoutingWatchKeyDelegate(@NotNull WatchKey delegate,
                               @NotNull MultiRoutingFileSystemProvider provider,
                               @NotNull MultiRoutingWatchServiceDelegate owner) {
    myDelegate = delegate;
    myProvider = provider;
    myOwner = owner;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public List<WatchEvent<?>> pollEvents() {
    List<WatchEvent<?>> events = new ArrayList<>(myDelegate.pollEvents());
    ListIterator<WatchEvent<?>> iterator = events.listIterator();
    while (iterator.hasNext()) {
      iterator.set(new MultiRoutingWatchEventDelegate<>(iterator.next(), myProvider));
    }
    return events;
  }

  @Override
  public boolean reset() {
    boolean result = myDelegate.reset();
    if (!result) {
      myOwner.forgetDelegateKey(myDelegate);
    }
    return result;
  }

  @Override
  public void cancel() {
    try {
      myDelegate.cancel();
    }
    finally {
      myOwner.forgetDelegateKey(myDelegate);
    }
  }

  @Override
  public Watchable watchable() {
    Watchable watchable = myDelegate.watchable();
    if (watchable instanceof Path path) {
      // the wrapped watch service should not leak backend paths through the MRFS API.
      return myProvider.wrapDelegatePath(path);
    }
    return watchable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MultiRoutingWatchKeyDelegate that)) return false;
    return myDelegate.equals(that.myDelegate) && myProvider.equals(that.myProvider);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDelegate, myProvider);
  }
}
