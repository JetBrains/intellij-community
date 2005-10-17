package com.intellij.mock;

import com.intellij.openapi.vfs.*;

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

  public void removeVirtualFileListener(VirtualFileListener listener) {
  }

  public void dispatchPendingEvent(VirtualFileListener listener) {
  }

  public void addModificationAttemptListener(ModificationAttemptListener listener) {
  }

  public void removeModificationAttemptListener(ModificationAttemptListener listener) {
  }

  public void fireReadOnlyModificationAttempt(VirtualFile[] files) {
  }
}
