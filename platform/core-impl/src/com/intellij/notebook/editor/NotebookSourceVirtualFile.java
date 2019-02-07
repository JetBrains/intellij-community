// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notebook.editor;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * File with source code representation of Scientific notebook content.
 *
 * Typically this is <code>{@link com.intellij.testFramework.LightVirtualFile}</code>) instance that transformed content of
 * original {@code VirtualFile} opened in the Editor.
 *
 * For additional details see: https://confluence.jetbrains.com/display/PYINT/Scientific+Notebooks
 *
 * This is a temporary interface that is essential to support Scientific Notebooks.
 * It may be removed in the future and shouldn't be used outside of notebooks support.
 */
public interface NotebookSourceVirtualFile {
  /**
   * Returns the origin {@link VirtualFile} with notebook content (e.g. file with JSON for the Jupyter notebook).
   */
  VirtualFile getOriginFile();
}
