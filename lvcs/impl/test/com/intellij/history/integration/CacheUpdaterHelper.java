package com.intellij.history.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class CacheUpdaterHelper {
  public static void performUpdate(CacheUpdater u) {
    performUpdate(u, null);
  }

  public static void performUpdate(CacheUpdater u, String physicalContent) {
    for (VirtualFile f : u.queryNeededFiles()) {
      u.processFile(fileContentOf(f, physicalContent));
    }
    u.updatingDone();
  }

  public static FileContent fileContentOf(VirtualFile f) {
    return fileContentOf(f, null);
  }

  public static FileContent fileContentOf(final VirtualFile f, final String physicalContent) {
    return new FileContent(f) {
      @Override
      public byte[] getPhysicalBytes() throws IOException {
        return physicalContent == null ? f.contentsToByteArray() : physicalContent.getBytes();
      }

      @Override
      public long getPhysicalLength() throws IOException {
        return getPhysicalBytes().length;
      }
    };
  }
}
