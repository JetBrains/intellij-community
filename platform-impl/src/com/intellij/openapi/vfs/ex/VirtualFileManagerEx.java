
package com.intellij.openapi.vfs.ex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;

public abstract class VirtualFileManagerEx extends VirtualFileManager {

  public abstract void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction);
  public abstract void afterRefreshFinish(boolean asynchronous, ModalityState modalityState);
  public abstract void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState);

  public abstract void registerRefreshUpdater(CacheUpdater updater);
  public abstract void unregisterRefreshUpdater(CacheUpdater updater);

  public abstract void registerFileSystem(VirtualFileSystem fileSystem);
  public abstract void unregisterFileSystem(VirtualFileSystem fileSystem);

  public abstract void fireBeforeRefreshStart(boolean asynchronous);

  public abstract void fireAfterRefreshFinish(boolean asynchronous);
}
