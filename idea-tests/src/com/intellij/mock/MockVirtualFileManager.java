package com.intellij.mock;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NotNull;

public class MockVirtualFileManager extends VirtualFileManagerEx {
  public MockVirtualFileManager() {
    super();
  }

  public VirtualFileSystem[] getFileSystems() {
    return new VirtualFileSystem[0];
  }

  public VirtualFileSystem getFileSystem(String protocol) {
    return null;
  }

  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    refresh(asynchronous);
  }

  public void refresh(boolean asynchronous) {
  }

  public void refresh(boolean asynchronous, Runnable postAction) {
  }

  public VirtualFile findFileByUrl(@NotNull String url) {
    return null;
  }

  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return null;
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
  }

  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
  }

  public void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction) {
  }

  public void afterRefreshFinish(boolean asynchronous, ModalityState modalityState) {
  }

  public void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState) {
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
  }

  public void registerFileSystem(VirtualFileSystem fileSystem) {
  }

  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
  }

  public void fireAfterRefreshFinish(final boolean asynchronous) {

  }

  public void fireBeforeRefreshStart(final boolean asynchronous) {
    
  }

}
