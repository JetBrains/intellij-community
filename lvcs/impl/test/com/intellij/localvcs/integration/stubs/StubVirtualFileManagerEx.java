package com.intellij.localvcs.integration.stubs;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
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

  public void dispatchPendingEvent(@NotNull VirtualFileListener listener) {
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

  public void registerFileContentProvider(FileContentProvider provider) {
    throw new UnsupportedOperationException();
  }

  public void unregisterFileContentProvider(FileContentProvider provider) {
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

  @Nullable
  public ProvidedContent getProvidedContent(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public void fireAfterRefreshFinish(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }

  public void fireBeforeRefreshStart(boolean asynchronous) {
    throw new UnsupportedOperationException();
  }
}
