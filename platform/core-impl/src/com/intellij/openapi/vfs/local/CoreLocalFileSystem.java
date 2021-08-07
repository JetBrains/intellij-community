// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class CoreLocalFileSystem extends DeprecatedVirtualFileSystem {
  @NotNull
  @Override
  public String getProtocol() {
    return StandardFileSystems.FILE_PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByIoFile(@NotNull File ioFile) {
    return ioFile.exists() ? new CoreLocalVirtualFile(this, ioFile) : null;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return findFileByIoFile(new File(path));
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }
}
