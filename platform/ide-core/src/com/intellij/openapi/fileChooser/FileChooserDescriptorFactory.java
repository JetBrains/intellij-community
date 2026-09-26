// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Common variants of {@link FileChooserDescriptor}.
 */
public final class FileChooserDescriptorFactory {
  private FileChooserDescriptorFactory() { }

  public static FileChooserDescriptor singleFile() {
    return new FileChooserDescriptor(true, false, false, false);
  }

  public static FileChooserDescriptor singleDir() {
    return new FileChooserDescriptor(false, true, false, false);
  }

  public static FileChooserDescriptor singleFileOrDir() {
    return new FileChooserDescriptor(true, true, false, false);
  }

  /**
   * On macOS, allows selecting a single file or an application bundle directory (e.g., "/Applications/XCode.app");
   * on other systems is equivalent to {@link #singleFile()}.
   */
  public static FileChooserDescriptor singleFileOrAppBundle() {
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
      return singleFile();
    }
  }

  public static FileChooserDescriptor multiFiles() {
    return new FileChooserDescriptor(true, false, false, true);
  }

  public static FileChooserDescriptor multiDirs() {
    return new FileChooserDescriptor(false, true, false, true);
  }

  public static FileChooserDescriptor multiFilesOrDirs() {
    return new FileChooserDescriptor(true, true, false, true);
  }

  public static FileChooserDescriptor multiJarsOrDirs() {
    return new FileChooserDescriptor(false, true, true, true);
  }

  /** Verbose and complicated name; consider using {@link #multiFilesOrDirs()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createAllButJarContentsDescriptor() {
    return multiFilesOrDirs();
  }

  /** Verbose and complicated name; consider using {@link #multiFiles()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createMultipleFilesNoJarsDescriptor() {
    return multiFiles();
  }

  /** Verbose name; consider using {@link #multiDirs()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createMultipleFoldersDescriptor() {
    return multiDirs();
  }

  /** Verbose and complicated name; consider using {@link #singleFile()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileNoJarsDescriptor() {
    return singleFile();
  }

  /** Verbose and complicated name; consider using {@link #singleFileOrAppBundle()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileOrExecutableAppDescriptor() {
    return singleFileOrAppBundle();
  }

  /** @deprecated misleading (allows directories); consider using {@link #singleFile()} or some other method instead */
  @Deprecated
  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, false, false);
  }

  /** @deprecated misleading (allows directories); consider using {@link #singleFile()} or another method instead */
  @Deprecated
  public static FileChooserDescriptor createSingleFileDescriptor() {
    return createSingleLocalFileDescriptor();
  }

  /** Verbose name; consider using {@link #singleFile()} together with {@link FileChooserDescriptor#withExtensionFilter} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileDescriptor(@NotNull FileType fileType) {
    return singleFile().withExtensionFilter(fileType);
  }

  /** Verbose name; consider using {@link #singleFileOrDir()} together with {@link FileChooserDescriptor#withExtensionFilter} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileOrFolderDescriptor(@NotNull FileType fileType) {
    return singleFileOrDir().withExtensionFilter(fileType);
  }

  /** Verbose name; consider using {@link #singleFile()} together with {@link FileChooserDescriptor#withExtensionFilter} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileDescriptor(@NotNull String extension) {
    return singleFile().withExtensionFilter(extension);
  }

  /** Verbose name; consider using {@link #singleDir()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFolderDescriptor() {
    return singleDir();
  }

  /** Verbose name; consider using {@link #multiJarsOrDirs()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createMultipleJavaPathDescriptor() {
    return multiJarsOrDirs();
  }

  /** Verbose name; consider using {@link #singleFileOrDir()} instead. */
  @ApiStatus.Obsolete
  public static FileChooserDescriptor createSingleFileOrFolderDescriptor() {
    return singleFileOrDir();
  }
}
