// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import org.jetbrains.annotations.NotNull;

final class FileBasedIndexFileTypeListener implements FileTypeListener {
  @Override
  public void fileTypesChanged(@NotNull final FileTypeEvent event) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    if (fileBasedIndex.getRegisteredIndexes() == null) {
      return;
    }

    String message = "File type change: " + "added - " + event.getAddedFileType() + ", removed - " + event.getRemovedFileType();
    fileBasedIndex.scheduleFullIndexesRescan(message);
  }
}
