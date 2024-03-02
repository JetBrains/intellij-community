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
 */
@ApiStatus.Experimental
public interface BackFileViewProvider {
  Key<VirtualFile> FRONT_FILE_KEY = new Key<>("FRONT_FILE_KEY");

  @Nullable
  PsiFile getFrontPsiFile();

  @Nullable
  VirtualFile getFrontVirtualFile();
}