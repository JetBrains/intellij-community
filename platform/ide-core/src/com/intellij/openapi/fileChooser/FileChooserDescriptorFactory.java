// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;

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
    if (OS.CURRENT == OS.macOS) {
      return new FileChooserDescriptor(true, true, false, false) {
        @Override
        public void validateSelectedFiles(@NotNull VirtualFile @NotNull [] files) throws Exception {
          var file = files[0];
          if (file.isDirectory() && !"app".equals(file.getExtension())) {
            throw new Exception(IdeCoreBundle.message("file.chooser.not.app.bundle", file.getPresentableUrl()));
          }
        }
      };
    }
    else {
      return createSingleFileNoJarsDescriptor();
    }
  }

  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false);
  }

  public static FileChooserDescriptor createSingleFileDescriptor() {
    return createSingleLocalFileDescriptor();
  }

  public static FileChooserDescriptor createSingleFileDescriptor(@NotNull FileType fileType) {
    return createSingleFileNoJarsDescriptor().withExtensionFilter(fileType);
  }

  public static FileChooserDescriptor createSingleFileOrFolderDescriptor(@NotNull FileType fileType) {
    return createSingleFileOrFolderDescriptor().withExtensionFilter(fileType);
  }

  public static FileChooserDescriptor createSingleFileDescriptor(@NotNull String extension) {
    return createSingleFileNoJarsDescriptor().withExtensionFilter(extension);
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
}
