// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notebook.editor;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * File that is created on top of another file and contains its content transformed in some predictable way.
 *
 * Typically this is <code>{@link com.intellij.testFramework.LightVirtualFile}</code>) instance that contains transformed content of
 * original {@code VirtualFile} opened in the Editor.
 *
 * This is a temporary interface that may be removed in the future.
 */
@ApiStatus.Experimental
public interface BackedVirtualFile {
  /**
   * Returns the {@link VirtualFile} with the original content.
   */
  VirtualFile getOriginFile();

  /**
   * Returns the {@link VirtualFile} with the original content if the file is Backed or the file in another case.
   */
  static VirtualFile getOriginFileIfBacked(VirtualFile file) {
    if (file instanceof BackedVirtualFile) {
      return ((BackedVirtualFile)file).getOriginFile();
    }
    return file;
  }
}
