// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors.impl;

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.descriptors.ConfigFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class ConfigFileVfsListener implements AsyncFileListener {

  @Override
  public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    final ArrayList<VirtualFile> filesToUpdate = new ArrayList<>();
    for (VFileEvent event : events) {
      if ((event instanceof VFilePropertyChangeEvent propEvent && propEvent.getPropertyName().equals(VirtualFile.PROP_NAME)) ||
          (event instanceof VFileMoveEvent)) {
        filesToUpdate.add(event.getFile());
      }
    }

    if (filesToUpdate.isEmpty()) {
      return null;
    }
    else {
      return new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          ConfigFileFactoryImpl service = (ConfigFileFactoryImpl)ConfigFileFactory.getInstance();
          service.handleFileChanges(filesToUpdate);
        }
      };
    }
  }
}
