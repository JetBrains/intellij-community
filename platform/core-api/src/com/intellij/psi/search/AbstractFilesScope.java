// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSet;
import com.intellij.openapi.vfs.VirtualFileSetEx;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.Internal
public abstract class AbstractFilesScope extends GlobalSearchScope implements VirtualFileEnumeration {
  volatile Boolean myHasFilesOutOfProjectRoots;

  /** @param hasFilesOutOfProjectRoots optimization */
  AbstractFilesScope(@Nullable Project project, @Nullable Boolean hasFilesOutOfProjectRoots) {
    super(project);
    myHasFilesOutOfProjectRoots = hasFilesOutOfProjectRoots;
  }

  @Override
  public @Nullable @Unmodifiable Collection<VirtualFile> getFilesIfCollection() {
    return getFiles();
  }

  abstract @NotNull @Unmodifiable VirtualFileSet getFiles();

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getFiles().contains(file);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return hasFilesOutOfProjectRoots();
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof AbstractFilesScope && getFiles().equals(((AbstractFilesScope)o).getFiles());
  }

  @Override
  public int calcHashCode() {
    return getFiles().hashCode();
  }

  private boolean hasFilesOutOfProjectRoots() {
    Boolean result = myHasFilesOutOfProjectRoots;
    if (result == null) {
      Project project = getProject();
      myHasFilesOutOfProjectRoots = result =
        project != null && !project.isDefault() &&
        ContainerUtil.find(getFiles(), file -> FileIndexFacade.getInstance(project).getModuleForFile(file) != null) == null;
    }
    return result;
  }

  @Override
  public boolean contains(int fileId) {
    return ((VirtualFileSetEx)getFiles()).containsId(fileId);
  }

  @Override
  public int @NotNull [] asArray() {
    return ((VirtualFileSetEx)getFiles()).onlyInternalFileIds();
  }
}
