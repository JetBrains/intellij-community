// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Overrides encoding specified in {@link EncodingRegistry} for an arbitrary file. Doesn't affect files defining
 * their own encoding via {@code LanguageFileType.getCharset()}
 */
public interface FileEncodingProvider {
  ExtensionPointName<FileEncodingProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileEncodingProvider");

  @Nullable
  Charset getEncoding(@NotNull Project project, @NotNull VirtualFile virtualFile);

  static Charset getFileEncoding(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    for (FileEncodingProvider encodingProvider : EP_NAME.getIterable()) {
      Charset encoding = encodingProvider.getEncoding(project, virtualFile);
      if (encoding != null) return encoding;
    }
    return null;
  }
}
