/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CoreVirtualFilePointerManager extends VirtualFilePointerManager {
  @NotNull
  @Override
  public VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
    return new IdentityVirtualFilePointer(vFile, url);
  }

  @NotNull
  @Override
  public VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return new IdentityVirtualFilePointer(file, file.getUrl());
  }

  @NotNull
  @Override
  public VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer,
                                      @NotNull Disposable parent,
                                      @Nullable VirtualFilePointerListener listener) {
    return new IdentityVirtualFilePointer(pointer.getFile(), pointer.getUrl());
  }

  @NotNull
  @Override
  public VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @NotNull
  @Override
  public VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return new VirtualFilePointerContainerImpl(this, parent, listener);
  }

  @Override
  public void dispose() {
  }

  @Override
  public long getModificationCount() {
    return 0;
  }
}
