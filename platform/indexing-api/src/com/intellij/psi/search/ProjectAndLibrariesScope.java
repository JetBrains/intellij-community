// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId;
import com.intellij.projectModel.ModuleDependenciesGraphService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

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
    Project project = getProject();
    if (project == null) return 0;

    var result = 0;
    var sdks1 = myProjectFileIndex.findContainingSdks(file1);
    var sdks2 = myProjectFileIndex.findContainingSdks(file2);
    if (!sdks1.isEmpty() && !sdks2.isEmpty()) {
      result = compareFirstItems(project, sdks1, sdks2);
    }

    var libs1 = myProjectFileIndex.findContainingLibraries(file1);
    var libs2 = myProjectFileIndex.findContainingLibraries(file2);
    if (!libs1.isEmpty() && !libs2.isEmpty()) {
      result = compareFirstItems(project, libs1, libs2);
    }
    return result;
  }

  private static int compareFirstItems(Project project,
                                       Collection<? extends WorkspaceEntityWithSymbolicId> items1,
                                       Collection<? extends WorkspaceEntityWithSymbolicId> items2) {
    int result = 0;
    var item1 = ContainerUtil.getFirstItem(items1);
    var item2 = ContainerUtil.getFirstItem(items2);
    var dependenciesGraph = ModuleDependenciesGraphService.getInstance(project).getModuleDependenciesGraph();
    var item1Dependants = ContainerUtil.sorted(dependenciesGraph.getLibraryOrSdkDependants(item1.getSymbolicId()),
                                               Comparator.comparing(dep -> dep.getDependent().getName()));
    var item2Dependants = ContainerUtil.sorted(dependenciesGraph.getLibraryOrSdkDependants(item2.getSymbolicId()),
                                               Comparator.comparing(dep -> dep.getDependent().getName()));

    for (var pair : ContainerUtil.zip(item1Dependants, item2Dependants)) {
      var first = pair.getFirst();
      var second = pair.getSecond();
      if (first.getDependent().equals(second.getDependent())) {
        result = Integer.compare(second.getOrderNumber(), first.getOrderNumber());
        if (result != 0) {
          return result;
        }
      }
    }
    return result;
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
