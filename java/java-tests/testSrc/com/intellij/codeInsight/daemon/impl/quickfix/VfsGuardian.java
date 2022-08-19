// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class VfsGuardian {
  private static final Logger LOG = Logger.getInstance(VfsGuardian.class);
  /**
   * Listens for changes in files under {@code root} and reverts them back when {@code parent} gets disposed
   */
  static void guard(@NotNull String root, @NotNull Disposable parent) {
    Map<VirtualFile, byte[]> oldContent = new HashMap<>();
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void beforeContentsChange(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (file.getPath().startsWith(root)) {
          try {
            byte[] old = file.contentsToByteArray();
            oldContent.put(file, old);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }, parent);
    Disposer.register(parent, () -> {
      for (Map.Entry<VirtualFile, byte[]> entry : oldContent.entrySet()) {
        VirtualFile file = entry.getKey();
        byte[] content = entry.getValue();
        try {
          LOG.warn("Restoring "+file+" ...");
          WriteAction.run(() -> file.setBinaryContent(content));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
