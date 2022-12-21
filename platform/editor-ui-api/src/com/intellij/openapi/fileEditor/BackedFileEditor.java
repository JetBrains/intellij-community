// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * Editor that has BackedVirtualFile as a file
 *
 * @see com.intellij.notebook.editor.BackedVirtualFile
 */
@ApiStatus.Experimental
public interface BackedFileEditor extends FileEditor {
  /**
   * Returns backed file. Note that getFile() should return original file instead.
   * @see #getFile()
   */
  VirtualFile getBackedFile();
}
