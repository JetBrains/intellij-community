package com.intellij.ide.presentation;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

/**
 * @author yole
 */
public class VirtualFilePresentation {
  public static Icon getIcon(VirtualFile vFile) {
    Icon icon = TypePresentationService.getService().getIcon(vFile);
    if (icon != null) {
      return icon;
    }
    if (vFile.isDirectory() && vFile.isInLocalFileSystem()) {
      return PlatformIcons.FOLDER_ICON;
    }
    return vFile.getFileType().getIcon();
  }
}
