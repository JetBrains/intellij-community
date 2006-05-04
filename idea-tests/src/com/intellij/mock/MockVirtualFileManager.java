package com.intellij.mock;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

public class MockVirtualFileManager extends VirtualFileManager {
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
}
