// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.presentation;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.fileTypes.DirectoryFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class VirtualFilePresentation {
  public static Icon getIcon(@NotNull VirtualFile vFile) {
    return IconUtil.getIcon(vFile, 0, null);
  }

  public static Icon getIconImpl(@NotNull VirtualFile vFile) {
    Icon icon = TypePresentationService.getService().getIcon(vFile);
    if (icon != null) {
      return icon;
    }
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(vFile.getNameSequence());
    if (vFile.isDirectory() && vFile.isInLocalFileSystem() && !(fileType instanceof DirectoryFileType)) {
      return PlatformIcons.FOLDER_ICON;
    }
    return fileType.getIcon();
  }
}