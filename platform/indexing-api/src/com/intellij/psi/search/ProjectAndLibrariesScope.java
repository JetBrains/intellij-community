// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Scope for all things inside the project: files in the project content plus files in libraries/libraries sources
 */
public class ProjectAndLibrariesScope extends GlobalSearchScope {
  protected final ProjectFileIndex myProjectFileIndex;
  private @Nls String myDisplayName;

  public ProjectAndLibrariesScope(@NotNull Project project) {
    super(project);
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myProjectFileIndex.isInProject(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    List<OrderEntry> entries1 = myProjectFileIndex.getOrderEntriesForFile(file1);
    List<OrderEntry> entries2 = myProjectFileIndex.getOrderEntriesForFile(file2);
    if (entries1.size() != entries2.size()) return 0;

    int res = 0;
    for (OrderEntry entry1 : entries1) {
      Module module = entry1.getOwnerModule();
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
      OrderEntry entry2 = moduleFileIndex.getOrderEntryForFile(file2);
      if (entry2 == null) {
        return 0;
      }
      else {
        int aRes = entry2.compareTo(entry1);
        if (aRes == 0) return 0;
        if (res == 0) {
          res = aRes;
        }
        else if (res != aRes) {
          return 0;
        }
      }
    }

    return res;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public @NotNull @Unmodifiable Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    return project != null ? ModuleManager.getInstance(project).getUnloadedModuleDescriptions() : Collections.emptySet();
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName == null ? getNameText() : myDisplayName;
  }

  public static @NotNull @Nls String getNameText() {
    return IndexingBundle.message("psi.search.scope.project.and.libraries");
  }

  public void setDisplayName(@NotNull @Nls String displayName) {
    myDisplayName = displayName;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
