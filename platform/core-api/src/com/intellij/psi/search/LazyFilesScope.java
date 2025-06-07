// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

class LazyFilesScope extends AbstractFilesScope {
  private volatile VirtualFileSet myFiles;
  private @NotNull Supplier<? extends Collection<? extends VirtualFile>> myFilesSupplier;

  LazyFilesScope(@Nullable Project project, @NotNull Supplier<? extends Collection<? extends VirtualFile>> files) {
    super(project, null);
    myFilesSupplier = files;
  }

  @Override
  public @NotNull VirtualFileSet getFiles() {
    if (myFiles == null) {
      synchronized (this) {
        if (myFiles == null) {
          VirtualFileSet fileSet = VfsUtilCore.createCompactVirtualFileSet(myFilesSupplier.get());
          fileSet.freeze();
          myFilesSupplier = null;
          myFiles = fileSet;
        }
      }
    }
    return myFiles;
  }

  @Override
  public String toString() {
    return "Files: [" +
           (myFiles == null ? "(not loaded yet)" : StringUtil.join(myFiles, ", ")) +
           "]; search in libraries: " +
           (myHasFilesOutOfProjectRoots != null ? myHasFilesOutOfProjectRoots : "unknown");
  }
}
