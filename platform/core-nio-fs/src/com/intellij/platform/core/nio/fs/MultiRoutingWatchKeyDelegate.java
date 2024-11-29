// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

class MultiRoutingWatchKeyDelegate implements WatchKey {
  @NotNull private final WatchKey myDelegate;
  @NotNull private final MultiRoutingFileSystemProvider myProvider;

  MultiRoutingWatchKeyDelegate(@NotNull WatchKey delegate, @NotNull MultiRoutingFileSystemProvider provider) {
    myDelegate = delegate;
    myProvider = provider;
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
    return myDelegate.reset();
  }

  @Override
  public void cancel() {
    myDelegate.cancel();
  }

  @Override
  public Watchable watchable() {
    return myDelegate.watchable();
  }
}
