/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.stubs;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubVirtualFileManagerEx extends VirtualFileManagerEx {
  public VirtualFileSystem[] getFileSystems() {
    throw new UnsupportedOperationException();
  }

  public VirtualFileSystem getFileSystem(String protocol) {
    throw new UnsupportedOperationException();
  }

  public void refresh(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }

  public void refresh(boolean asynchronous, @Nullable Runnable postAction) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VirtualFile findFileByUrl(@NonNls @NotNull String url) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    throw new UnsupportedOperationException();
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    throw new UnsupportedOperationException();
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    throw new UnsupportedOperationException();
  }

  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    throw new UnsupportedOperationException();
  }

  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    throw new UnsupportedOperationException();
  }

  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
    throw new UnsupportedOperationException();
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
    throw new UnsupportedOperationException();
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
    throw new UnsupportedOperationException();
  }

  public void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction) {
    throw new UnsupportedOperationException();
  }

  public void afterRefreshFinish(boolean asynchronous, ModalityState modalityState) {
    throw new UnsupportedOperationException();
  }

  public void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState) {
    throw new UnsupportedOperationException();
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
    throw new UnsupportedOperationException();
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
    throw new UnsupportedOperationException();
  }

  public void registerFileSystem(VirtualFileSystem fileSystem) {
    throw new UnsupportedOperationException();
  }

  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
    throw new UnsupportedOperationException();
  }

  public void refreshWithoutFileWatcher(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }

  public void fireAfterRefreshFinish(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }

  public void fireBeforeRefreshStart(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }

  public long getModificationCount() {
    return ModificationTracker.EVER_CHANGED.getModificationCount();
  }
}
