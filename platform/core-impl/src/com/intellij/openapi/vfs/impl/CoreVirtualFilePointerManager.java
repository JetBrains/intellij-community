// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public class CoreVirtualFilePointerManager extends VirtualFilePointerManager implements Disposable {
  @Override
  public @NotNull VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return new LightFilePointer(url);
  }

  @Override
  public @NotNull VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return new LightFilePointer(file);
  }

  @Override
  public @NotNull VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer,
                                               @NotNull Disposable parent,
                                               @Nullable VirtualFilePointerListener listener) {
    return new LightFilePointer(pointer.getUrl());
  }

  @Override
  public @NotNull VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @Override
  public @NotNull VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return new VirtualFilePointerContainerImpl(this, parent, listener);
  }

  @Override
  public @NotNull VirtualFilePointer createDirectoryPointer(@NotNull String url,
                                                            boolean recursively,
                                                            @NotNull Disposable parent, @NotNull VirtualFilePointerListener listener) {
    return create(url, parent, listener);
  }

  @Override
  public void dispose() {

  }
}
