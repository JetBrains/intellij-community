package com.intellij.localvcs.integration.stubs;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StubVirtualFileManager extends VirtualFileManager {
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
}
