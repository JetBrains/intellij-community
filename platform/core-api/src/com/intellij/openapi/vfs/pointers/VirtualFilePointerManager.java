// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VirtualFilePointerManager extends SimpleModificationTracker {
  public static VirtualFilePointerManager getInstance() {
    return ApplicationManager.getApplication().getService(VirtualFilePointerManager.class);
  }

  @NotNull
  public abstract VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer, @NotNull Disposable parent,
                                               @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointerContainer createContainer(@NotNull Disposable parent);

  @NotNull
  public abstract VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointer createDirectoryPointer(@NotNull String url,
                                                            boolean recursively,
                                                            @NotNull Disposable parent,
                                                            @NotNull VirtualFilePointerListener listener);
}
