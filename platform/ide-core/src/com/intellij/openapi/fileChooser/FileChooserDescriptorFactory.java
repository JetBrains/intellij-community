// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common variants of {@link FileChooserDescriptor}.
 */
public final class FileChooserDescriptorFactory {
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
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return super.isFileSelectable(file) || file != null && SystemInfo.isMac && file.isDirectory() && "app".equals(file.getExtension());
      }
    };
  }

  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false);
  }

  public static FileChooserDescriptor createSingleFileDescriptor() {
    return createSingleLocalFileDescriptor();
  }

  public static FileChooserDescriptor createSingleFileDescriptor(@NotNull FileType fileType) {
    return new FileChooserDescriptor(true, false, false, false, false, false)
      .withFileFilter(file -> FileTypeRegistry.getInstance().isFileOfType(file, fileType));
  }

  public static FileChooserDescriptor createSingleFileDescriptor(final String extension) {
    return new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(
      file -> Comparing.equal(file.getExtension(), extension, file.isCaseSensitive()));
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

  public static FileChooserDescriptor createSingleFileOrFolderDescriptor(@NotNull FileType fileType) {
    return new FileChooserDescriptor(true, true, false, false, false, false)
      .withFileFilter(file -> FileTypeRegistry.getInstance().isFileOfType(file, fileType));
  }
}
