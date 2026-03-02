// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.nio.file.Path;

@ApiStatus.Internal
public interface FileChooserCustomizer {
  ExtensionPointName<FileChooserCustomizer> EP_NAME = ExtensionPointName.create("com.intellij.fileChooserCustomizer");

  /**
   * Return {@code false} to explicitly prevent the directory/file being shown in the file chooser tree (roots)/recent files' combo.
   */
  default boolean isPathVisible(@Nullable Project project, @NotNull Path rootPath) { return true; }

  /**
   * Return the icon for the specified {@code filePath} without any I/O operations on it. The method is called from EDT, any significant
   * delays may cause UI freezes.
   */
  default @Nullable Icon fastGetIcon(@Nullable Project project, @NotNull Path filePath) { return null; }

  final class Util {
    public static boolean isPathVisible(@Nullable Project project, @NotNull Path path) {
      for (final var ex : EP_NAME.getExtensionList()) {
        if (!ex.isPathVisible(project, path)) return false;
      }
      return true;
    }

    public static @Nullable Icon fastGetFileIcon(@Nullable Project project, @NotNull Path filePath) {
      for (final var ex : EP_NAME.getExtensionList()) {
        final var fileIcon = ex.fastGetIcon(project, filePath);
        if (fileIcon != null) return fileIcon;
      }
      return null;
    }
  }
}
