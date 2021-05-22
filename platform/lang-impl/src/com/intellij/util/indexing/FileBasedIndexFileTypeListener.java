// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

final class FileBasedIndexFileTypeListener implements FileTypeListener {
  @Override
  public void fileTypesChanged(@NotNull final FileTypeEvent event) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();

    if (fileBasedIndex.getRegisteredIndexes() == null) {
      return;
    }

    Collection<ID<?, ?>> indexesToRebuild = FileBasedIndexExtension.EXTENSION_POINT_NAME
      .extensions()
      .filter(ex -> IndexVersion.versionDiffers(ex.getName(), FileBasedIndexImpl.getIndexExtensionVersion(ex)) !=
                    IndexVersion.IndexVersionDiff.UP_TO_DATE)
      .map(ex -> ex.getName())
      .collect(Collectors.toList());

    String rebuiltIndexesLog = indexesToRebuild.isEmpty()
                               ? ""
                               : "; indexes " + indexesToRebuild + " will be rebuild completely due to version change";
    fileBasedIndex.scheduleFullIndexesRescan(indexesToRebuild, "File type change: " +
                                                               "added - " + event.getAddedFileType() + ", " +
                                                               "removed - " + event.getRemovedFileType() +
                                                               rebuiltIndexesLog);
  }
}
