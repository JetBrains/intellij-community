// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;

public class FileChooserDescriptorFactory {
  private FileChooserDescriptorFactory() { }

  public static FileChooserDescriptor createAllButJarContentsDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, true);
  }

  public static FileChooserDescriptor createMultipleFilesNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, true);
  }

  public static FileChooserDescriptor createMultipleFoldersDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, true);
  }

  public static FileChooserDescriptor createSingleFileNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false);
  }

  public static FileChooserDescriptor createSingleFileOrExecutableAppDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) || SystemInfo.isMac && file.isDirectory() && "app".equals(file.getExtension());
      }
    };
  }

  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false);
  }

  public static FileChooserDescriptor createSingleFileDescriptor() {
    return createSingleLocalFileDescriptor();
  }

  public static FileChooserDescriptor createSingleFileDescriptor(final FileType fileType) {
    return new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(file -> FileTypeRegistry.getInstance().isFileOfType(file, fileType));
  }

  public static FileChooserDescriptor createSingleFileDescriptor(final String extension) {
    return new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(
      file -> Comparing.equal(file.getExtension(), extension, SystemInfo.isFileSystemCaseSensitive));
  }

  public static FileChooserDescriptor createSingleFolderDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, false);
  }

  public static FileChooserDescriptor createMultipleJavaPathDescriptor() {
    return new FileChooserDescriptor(false, true, true, false, true, true);
  }

  public static FileChooserDescriptor createSingleFileOrFolderDescriptor() {
    return new FileChooserDescriptor(true, true, false, false, false, false);
  }

  public static FileChooserDescriptor createSingleFileOrFolderDescriptor(final FileType fileType) {
    return new FileChooserDescriptor(true, true, false, false, false, false).withFileFilter(file -> FileTypeRegistry.getInstance().isFileOfType(file, fileType));
  }

  /**
   * @deprecated not very useful (to be removed in IDEA 15)
   */
  @Deprecated
  @SuppressWarnings("UnusedDeclaration")
  public static FileChooserDescriptor getDirectoryChooserDescriptor(String objectName) {
    return createSingleFolderDescriptor().withTitle("Select " + objectName);
  }
}
