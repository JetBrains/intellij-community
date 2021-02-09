// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

final class FileBasedIndexFileTypeListener implements FileTypeListener {
  @Override
  public void fileTypesChanged(@NotNull final FileTypeEvent event) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();

    if (fileBasedIndex.getRegisteredIndexes() == null) {
      return;
    }

    Set<ID<?, ?>> indexesToRebuild = new HashSet<>();
    for (FileBasedIndexExtension<?, ?> extension : FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList()) {
      if (IndexVersion.versionDiffers(extension.getName(), FileBasedIndexImpl.getIndexExtensionVersion(extension)) != IndexVersion.IndexVersionDiff.UP_TO_DATE) {
        indexesToRebuild.add(extension.getName());
      }
    }

    String rebuiltIndexesLog = indexesToRebuild.isEmpty()
                               ? ""
                               : "; indexes " + indexesToRebuild + " will be rebuild completely due to version change";
    fileBasedIndex.scheduleFullIndexesRescan(indexesToRebuild, "File type change: " +
                                                               "added - " + event.getAddedFileType() + ", " +
                                                               "removed - " + event.getRemovedFileType() +
                                                               rebuiltIndexesLog);
  }
}
