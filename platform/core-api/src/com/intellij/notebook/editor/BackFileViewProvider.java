// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebook.editor;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * This is interface should be inherited for that  files which differ in disk and editor representation
 * a Typical case is Jupyter notebooks, which have JSON and source text representation
 * @deprecated Will be fixed in <a href="https://youtrack.jetbrains.com/issue/PY-71854/Rewrite-NotebookVirtualFileSystem">PY-71854</a>
 */
@Deprecated
@ApiStatus.Internal
public interface BackFileViewProvider {
  Key<VirtualFile> FRONT_FILE_KEY = new Key<>("FRONT_FILE_KEY");
  /**
   * Heuristic for Front File size
   */
  Key<Float> FRONT_FILE_SIZE_RATIO_KEY = new Key<>("FRONT_FILE_KEY");

  @Nullable
  PsiFile getFrontPsiFile();

  @Nullable
  VirtualFile getFrontVirtualFile();
}