package com.intellij.history.integration.stubs;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.FileSystemSynchronizer;

public class StubStartupManagerEx extends StartupManagerEx {
  public void registerStartupActivity(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public void registerPostStartupActivity(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public void runWhenProjectIsInitialized(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public FileSystemSynchronizer getFileSystemSynchronizer() {
    throw new UnsupportedOperationException();
  }

  public boolean startupActivityRunning() {
    throw new UnsupportedOperationException();
  }

  public boolean startupActivityPassed() {
    throw new UnsupportedOperationException();
  }

  public void registerPreStartupActivity(Runnable runnable) // should be used only to register to FileSystemSynchronizer!
  {
    throw new UnsupportedOperationException();
  }
}
