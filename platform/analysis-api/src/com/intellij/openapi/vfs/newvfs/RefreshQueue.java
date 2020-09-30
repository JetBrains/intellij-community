// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class RefreshQueue {
  public static RefreshQueue getInstance() {
    return ApplicationManager.getApplication().getService(RefreshQueue.class);
  }

  @NotNull
  public final RefreshSession createSession(boolean async, boolean recursive, @Nullable Runnable finishRunnable) {
    return createSession(async, recursive, finishRunnable, ModalityState.defaultModalityState());
  }

  @NotNull
  public abstract RefreshSession createSession(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull ModalityState state);

  public final void refresh(boolean async, boolean recursive, @Nullable Runnable finishRunnable, VirtualFile @NotNull ... files) {
    refresh(async, recursive, finishRunnable, ModalityState.defaultModalityState(), files);
  }

  public final void refresh(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull Collection<? extends VirtualFile> files) {
    refresh(async, recursive, finishRunnable, ModalityState.defaultModalityState(), files);
  }

  public final void refresh(boolean async,
                            boolean recursive,
                            @Nullable Runnable finishRunnable,
                            @NotNull ModalityState state,
                            VirtualFile @NotNull ... files) {
    RefreshSession session = createSession(async, recursive, finishRunnable, state);
    session.addAllFiles(files);
    session.launch();
  }

  public final void refresh(boolean async,
                            boolean recursive,
                            @Nullable Runnable finishRunnable,
                            @NotNull ModalityState state,
                            @NotNull Collection<? extends VirtualFile> files) {
    RefreshSession session = createSession(async, recursive, finishRunnable, state);
    session.addAllFiles(files);
    session.launch();
  }

  @ApiStatus.Internal
  public abstract void processSingleEvent(@NotNull VFileEvent event);

  public abstract void cancelSession(long id);
}