package com.intellij.mock;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ide.startup.CacheUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public void refresh(boolean asynchronous) {
  }

  public void refresh(boolean asynchronous, Runnable postAction) {
  }

  public VirtualFile findFileByUrl(String url) {
    return null;
  }

  public VirtualFile refreshAndFindFileByUrl(String url) {
    return null;
  }

  public void addVirtualFileListener(VirtualFileListener listener) {
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
  }

  public void removeVirtualFileListener(VirtualFileListener listener) {
  }

  public void dispatchPendingEvent(VirtualFileListener listener) {
  }

  public void addModificationAttemptListener(ModificationAttemptListener listener) {
  }

  public void removeModificationAttemptListener(ModificationAttemptListener listener) {
  }

  public void fireReadOnlyModificationAttempt(VirtualFile... files) {
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

  public void registerFileContentProvider(FileContentProvider provider) {
  }

  public void unregisterFileContentProvider(FileContentProvider provider) {
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
  }

  @Nullable
  public ProvidedContent getProvidedContent(VirtualFile file) {
    return null;
  }
}
