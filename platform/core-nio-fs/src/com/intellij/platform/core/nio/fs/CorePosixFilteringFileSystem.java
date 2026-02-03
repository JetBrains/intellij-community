// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystem;

/**
 * @see CorePosixFilteringFileSystemProvider
 */
class CorePosixFilteringFileSystem extends DelegatingFileSystem<CorePosixFilteringFileSystemProvider> {
  private final @NotNull CorePosixFilteringFileSystemProvider myProvider;
  private final @NotNull FileSystem myDelegate;

  CorePosixFilteringFileSystem(@NotNull CorePosixFilteringFileSystemProvider provider, @NotNull FileSystem delegate) {
    myProvider = provider;
    myDelegate = delegate;
  }

  @Override
  public @NotNull FileSystem getDelegate() {
    return myDelegate;
  }

  @Override
  protected @NotNull FileSystem getDelegate(@NotNull String root) {
    return myDelegate;
  }

  @Override
  public CorePosixFilteringFileSystemProvider provider() {
    return myProvider;
  }
}
