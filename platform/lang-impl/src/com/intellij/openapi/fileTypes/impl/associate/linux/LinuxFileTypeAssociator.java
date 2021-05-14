// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.linux;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.fileTypes.impl.associate.SystemFileTypeAssociator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class LinuxFileTypeAssociator implements SystemFileTypeAssociator {
  @Override
  public void associateFileTypes(@NotNull List<? extends FileType> fileTypes) throws OSFileAssociationException {
    LinuxMimeTypeUpdater.updateMimeTypes(convertToMimeTypes(fileTypes));
  }

  private static List<MimeTypeDescription> convertToMimeTypes(@NotNull List<? extends FileType> fileTypes) {
    List<MimeTypeDescription> mimeTypeDescriptions =
      ContainerUtil.map(fileTypes, fileType -> new MimeTypeDescription(fileType));
    mimeTypeDescriptions.sort(Comparator.comparing(description -> description.getType()));
    return mimeTypeDescriptions;
  }
}
