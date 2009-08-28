package com.intellij.history.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.vfs.VirtualFile;

public class CacheUpdaterHelper {
  public static void performUpdate(CacheUpdater u) {
    for (VirtualFile f : u.queryNeededFiles()) {
      u.processFile(fileContentOf(f));
    }
    u.updatingDone();
  }

  public static FileContent fileContentOf(VirtualFile f) {
    return new FileContent(f);
  }
}
