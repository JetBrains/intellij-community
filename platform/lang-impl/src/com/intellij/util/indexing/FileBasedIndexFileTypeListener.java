// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import org.jetbrains.annotations.NotNull;

final class FileBasedIndexFileTypeListener implements FileTypeListener {
  @Override
  public void fileTypesChanged(final @NotNull FileTypeEvent event) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    if (fileBasedIndex.getRegisteredIndexes() == null) {
      return;
    }

    String message = "File type change: " + "added - " + event.getAddedFileType() + ", removed - " + event.getRemovedFileType();
    fileBasedIndex.scheduleFullIndexesRescan(message);
  }
}
