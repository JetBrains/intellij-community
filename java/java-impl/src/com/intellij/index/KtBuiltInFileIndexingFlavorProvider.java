// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.index;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.flavor.FileIndexingFlavorProvider;
import com.intellij.util.indexing.flavor.HashBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KtBuiltInFileIndexingFlavorProvider implements FileIndexingFlavorProvider<CharSequence> {
  @Override
  public @Nullable CharSequence getFlavor(@NotNull IndexedFile file) {
    VirtualFile vFile = file.getFile();
    VirtualFileSystem fs = vFile.getFileSystem();
    if (!(fs instanceof JarFileSystem)) return null;
    VirtualFile root = VfsUtilCore.getRootFile(vFile);
    return root.getNameSequence();
  }

  @Override
  public void buildHash(@NotNull CharSequence seq, @NotNull HashBuilder hashBuilder) {
    hashBuilder.putString(seq);
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public @NotNull String getId() {
    return "KtBuiltInFileIndexingFlavorProvider";
  }
}
