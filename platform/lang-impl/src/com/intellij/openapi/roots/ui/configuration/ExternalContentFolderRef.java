// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of {@link ContentFolderRef} which doesn't correspond to a real {@link ContentFolder} in the project root model.
 */
public final class ExternalContentFolderRef implements ContentFolderRef {
  private final VirtualFilePointer myFilePointer;

  public ExternalContentFolderRef(VirtualFilePointer filePointer) {
    myFilePointer = filePointer;
  }

  @Override
  public @Nullable VirtualFile getFile() {
    return myFilePointer.getFile();
  }

  @Override
  public @NotNull String getUrl() {
    return myFilePointer.getUrl();
  }

  @Override
  public @Nullable ContentFolder getContentFolder() {
    return null;
  }
}
