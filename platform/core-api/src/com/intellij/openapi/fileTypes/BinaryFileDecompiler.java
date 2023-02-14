// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Allows viewing binary files (i.e. those with {@link FileType#isBinary} returning {@code true}) in an editor.
 * Registration is via {@code "com.intellij.filetype.decompiler"} extension point (see {@link BinaryFileTypeDecompilers#EP_NAME}).
 */
public interface BinaryFileDecompiler {
  /**
   * The method is called from {@link FileDocumentManager#getDocument(VirtualFile)}.
   */
  @NotNull CharSequence decompile(@NotNull VirtualFile file);
}
