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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class RefreshQueue {
  public static RefreshQueue getInstance() {
    return ServiceManager.getService(RefreshQueue.class);
  }

  public final RefreshSession createSession(boolean async, boolean recursive, @Nullable Runnable finishRunnable) {
    return createSession(async, recursive, finishRunnable, getDefaultModalityState());
  }

  public abstract RefreshSession createSession(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull ModalityState state);

  public final void refresh(boolean async, boolean recursive, @Nullable Runnable finishRunnable, VirtualFile... files) {
    refresh(async, recursive, finishRunnable, getDefaultModalityState(), files);
  }

  public final void refresh(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull Collection<VirtualFile> files) {
    refresh(async, recursive, finishRunnable, getDefaultModalityState(), files);
  }

  public final void refresh(boolean async,
                            boolean recursive,
                            @Nullable Runnable finishRunnable,
                            @NotNull ModalityState state,
                            VirtualFile... files) {
    RefreshSession session = createSession(async, recursive, finishRunnable, state);
    session.addAllFiles(files);
    session.launch();
  }

  public final void refresh(boolean async,
                            boolean recursive,
                            @Nullable Runnable finishRunnable,
                            @NotNull ModalityState state,
                            @NotNull Collection<VirtualFile> files) {
    RefreshSession session = createSession(async, recursive, finishRunnable, state);
    session.addAllFiles(files);
    session.launch();
  }

  public abstract void refreshLocalRoots(boolean async, @Nullable Runnable postAction, @NotNull ModalityState modalityState);

  public abstract void processSingleEvent(@NotNull VFileEvent event);

  @NotNull
  protected ModalityState getDefaultModalityState() {
    return ModalityState.NON_MODAL;
  }
}
