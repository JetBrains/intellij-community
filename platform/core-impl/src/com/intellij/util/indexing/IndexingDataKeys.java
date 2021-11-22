// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class IndexingDataKeys {
  /**
   * Use {@link FileContent#getFile()}} or {@link PsiFile#getVirtualFile()} instead.
   */
  public static final Key<VirtualFile> VIRTUAL_FILE = new Key<>("Context virtual file");
  /**
   * Use {@link FileContent#getPsiFile()} instead.
   */
  public static final Key<PsiFile> PSI_FILE = new Key<>("PSI for stubs");
  /**
   * Use {@link FileContent#getContentAsText()} instead.
   */
  public static final Key<CharSequence> FILE_TEXT_CONTENT_KEY = Key.create("file text content cached by stub indexer");
  public static final Key<Boolean> REBUILD_REQUESTED = Key.create("index.rebuild.requested");
}
