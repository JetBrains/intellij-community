// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Dialog to save a file.
 *
 * @author Konstantin Bulenkov
 * @see FileChooserFactory
 */
public interface FileSaverDialog {
  @Nullable VirtualFileWrapper save(@Nullable VirtualFile baseDir, @Nullable String filename);

  @Nullable VirtualFileWrapper save(@Nullable Path baseDir, @Nullable String filename);

  default @Nullable VirtualFileWrapper save(@Nullable String filename) {
    return save((Path)null, filename);
  }
}
