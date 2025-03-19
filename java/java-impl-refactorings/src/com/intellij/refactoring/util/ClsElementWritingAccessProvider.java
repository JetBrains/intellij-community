// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public final class ClsElementWritingAccessProvider extends WritingAccessProvider {
  @Override
  public @NotNull Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    return Collections.emptyList();
  }

  @Override
  public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
    return JarFileSystem.getInstance().getLocalByEntry(file) == null;
  }
}