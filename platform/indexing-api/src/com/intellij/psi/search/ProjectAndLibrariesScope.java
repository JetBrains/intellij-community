// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.SymbolicEntityId;
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId;
import com.intellij.projectModel.ModuleDependenciesGraph;
import com.intellij.projectModel.ModuleDependenciesGraphService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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

    var libs1 = myProjectFileIndex.findContainingLibraries(file1);
    var libs2 = myProjectFileIndex.findContainingLibraries(file2);
    if (!libs1.isEmpty() && !libs2.isEmpty()) {
      return compareFirstItems(project, libs1, libs2);
    }

    var sdks1 = myProjectFileIndex.findContainingSdks(file1);
    var sdks2 = myProjectFileIndex.findContainingSdks(file2);
    if (!sdks1.isEmpty() && !sdks2.isEmpty()) {
      return compareFirstItems(project, sdks1, sdks2);
    }
    return 0;
  }

  private static int compareFirstItems(Project project,
                                       Collection<? extends WorkspaceEntityWithSymbolicId> items1,
                                       Collection<? extends WorkspaceEntityWithSymbolicId> items2) {
    var item1 = ContainerUtil.getFirstItem(items1);
    var item2 = ContainerUtil.getFirstItem(items2);
    if (item1 == null || item2 == null) return 0;

    SymbolicEntityId<?> id1 = item1.getSymbolicId();
    SymbolicEntityId<?> id2 = item2.getSymbolicId();

    if (id1.equals(id2)) return 0;

    ModuleDependenciesGraph graph = ModuleDependenciesGraphService.getInstance(project).getModuleDependenciesGraph();
    Map<ModuleId, Integer> dependants1 = graph.getLibraryOrSdkDependants(id1);
    Map<ModuleId, Integer> dependants2 = graph.getLibraryOrSdkDependants(id2);

    boolean swapped = dependants2.size() < dependants1.size();
    Map<ModuleId, Integer> smaller = swapped ? dependants2 : dependants1;
    Map<ModuleId, Integer> larger = swapped ? dependants1 : dependants2;

    int result = 0;
    for (Map.Entry<ModuleId, Integer> entry : smaller.entrySet()) {
      Integer otherOrder = larger.get(entry.getKey());
      if (otherOrder == null) continue;

      int order1 = swapped ? otherOrder : entry.getValue();
      int order2 = swapped ? entry.getValue() : otherOrder;
      int moduleResult = Integer.compare(order2, order1);
      if (result == 0) {
        result = moduleResult;
      }
      else if (result != moduleResult) {
        return 0;
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
