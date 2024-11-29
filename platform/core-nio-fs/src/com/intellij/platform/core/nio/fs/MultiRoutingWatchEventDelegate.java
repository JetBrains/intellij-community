// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

class MultiRoutingWatchEventDelegate<T> implements WatchEvent<T> {
  @NotNull private final WatchEvent<T> myDelegate;
  @NotNull private final MultiRoutingFileSystemProvider myProvider;

  MultiRoutingWatchEventDelegate(@NotNull WatchEvent<T> delegate, @NotNull MultiRoutingFileSystemProvider provider) {
    myDelegate = delegate;
    myProvider = provider;
  }

  @Override
  public Kind<T> kind() {
    return myDelegate.kind();
  }

  @Override
  public int count() {
    return myDelegate.count();
  }

  @Override
  public T context() {
    T context = myDelegate.context();
    if (context instanceof Path path) {
      //noinspection unchecked
      return (T)myProvider.toDelegatePath(path);
    }
    return context;
  }
}
