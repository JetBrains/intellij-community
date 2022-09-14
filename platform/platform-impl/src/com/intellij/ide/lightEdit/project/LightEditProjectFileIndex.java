// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class LightEditProjectFileIndex implements ProjectFileIndex {
  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Override
  public boolean isInProjectOrExcluded(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Nullable
  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    return null;
  }

  @NotNull
  @Override
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    return null;
  }

  @Nullable
  @Override
  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    return null;
  }

  @Override
  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isInLibrary(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor) {
    return true;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter) {
    return true;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return true;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir,
                                              @NotNull ContentIterator processor,
                                              @Nullable VirtualFileFilter customFilter) {
    return true;
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    return LightEditDirectoryIndex.getFileInfo().isInProject(fileOrDir);
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    return false;
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    return false;
  }
}
