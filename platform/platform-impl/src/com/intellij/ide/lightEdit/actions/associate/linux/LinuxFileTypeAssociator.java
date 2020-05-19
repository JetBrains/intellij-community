// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.linux;

import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.ide.lightEdit.actions.associate.SystemFileTypeAssociator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class LinuxFileTypeAssociator implements SystemFileTypeAssociator {
  @Override
  public void associateFileTypes(@NotNull List<FileType> fileTypes) throws FileAssociationException {
    LinuxMimeTypeUpdater.updateMimeTypes(convertToMimeTypes(fileTypes));
  }

  private static List<MimeTypeDescription> convertToMimeTypes(@NotNull List<FileType> fileTypes) {
    List<MimeTypeDescription> mimeTypeDescriptions =
      ContainerUtil.map(fileTypes, fileType -> new MimeTypeDescription(fileType));
    mimeTypeDescriptions.sort(Comparator.comparing(description -> description.getType()));
    return mimeTypeDescriptions;
  }
}
