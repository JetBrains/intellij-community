// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.liveReload;

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class WebServerFileContentListener implements AsyncFileListener {

  private static final ChangeApplier RELOAD_ALL = new ChangeApplier() {
    @Override
    public void afterVfsChange() {
      WebServerPageConnectionService.getInstance().reloadAll();
    }
  };

  @Nullable
  @Override
  public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
    boolean hasRelatedFileChanged = false;
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file != null && WebServerPageConnectionService.getInstance().isFileRequested(file)) {
        hasRelatedFileChanged = true;
        break;
      }
    }
    if (!hasRelatedFileChanged) return null;

    return RELOAD_ALL;
  }
}
