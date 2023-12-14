// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class LightEditProjectFileIndex implements ProjectFileIndex {
  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Override
  public boolean isInProjectOrExcluded(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Override
  public @Nullable Module getModuleForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @Nullable Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    return null;
  }

  @Override
  public @NotNull Set<Module> getModulesForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    return Collections.emptySet();
  }

  @Override
  public @NotNull List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    return Collections.emptyList();
  }

  @Override
  public @Nullable VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @Nullable VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @Nullable VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @Nullable VirtualFile getContentRootForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    return null;
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
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
  public @NotNull Collection<@NotNull LibraryEntity> findContainingLibraries(@NotNull VirtualFile fileOrDir) {
    return Collections.emptyList();
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
    return false;
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

  @Override
  public @Nullable JpsModuleSourceRootType<?> getContainingSourceRootType(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public boolean isInGeneratedSources(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public @Nullable String getUnloadedModuleNameForFile(@NotNull VirtualFile fileOrDir) {
    return null;
  }
}
