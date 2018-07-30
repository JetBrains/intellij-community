/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Queue;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author max
 */
class ModuleWithDependentsScope extends GlobalSearchScope {
  private final Module myModule;

  private final ProjectFileIndexImpl myProjectFileIndex;
  private final Set<Module> myModules;

  ModuleWithDependentsScope(@NotNull Module module) {
    super(module.getProject());
    myModule = module;

    myProjectFileIndex = (ProjectFileIndexImpl)ProjectRootManager.getInstance(module.getProject()).getFileIndex();

    myModules = buildDependents(myModule);
  }

  @NotNull
  private static Set<Module> buildDependents(@NotNull Module module) {
    Set<Module> result = new THashSet<>();
    result.add(module);

    ModuleIndex index = getModuleIndex(module.getProject());

    Queue<Module> walkingQueue = new Queue<>(10);
    walkingQueue.addLast(module);

    Set<Module> processedExporting = new THashSet<>();
    while (!walkingQueue.isEmpty()) {
      Module current = walkingQueue.pullFirst();
      processedExporting.add(current);
      result.addAll(index.plainUsages.get(current));
      for (Module dependent : index.exportingUsages.get(current)) {
        result.add(dependent);
        if (processedExporting.add(dependent)) {
          walkingQueue.addLast(dependent);
        }
      }
    }
    return result;
  }

  private static class ModuleIndex {
    final MultiMap<Module, Module> plainUsages = MultiMap.create();
    final MultiMap<Module, Module> exportingUsages = MultiMap.create();
  }

  @NotNull
  private static ModuleIndex getModuleIndex(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      ModuleIndex index = new ModuleIndex();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry) {
            Module referenced = ((ModuleOrderEntry)orderEntry).getModule();
            if (referenced != null) {
              MultiMap<Module, Module> map = ((ModuleOrderEntry)orderEntry).isExported() ? index.exportingUsages : index.plainUsages;
              map.putValue(referenced, module);
            }
          }
        }
      }
      return CachedValueProvider.Result.create(index, ProjectRootManager.getInstance(project));
    });
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return contains(file, false);
  }

  boolean contains(@NotNull VirtualFile file, boolean myOnlyTests) {
    // optimization: fewer calls to getInfoForFileOrDirectory()
    DirectoryInfo info = myProjectFileIndex.getInfoForFileOrDirectory(file);
    Module moduleOfFile = info.getModule();
    if (moduleOfFile == null || !myModules.contains(moduleOfFile)) return false;
    if (myOnlyTests && !TestSourcesFilter.isTestSources(file, moduleOfFile.getProject())) return false;
    return ProjectFileIndexImpl.isFileInContent(file, info);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myModules.contains(aModule);
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    ModuleManager moduleManager = ModuleManager.getInstance(myModule.getProject());
    return ContainerUtil.mapNotNull(DirectoryIndex.getInstance(myModule.getProject()).getDependentUnloadedModules(myModule),
                                    moduleManager::getUnloadedModuleDescription);
  }

  @Override
  @NonNls
  public String toString() {
    return "Module with dependents:" + myModule.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleWithDependentsScope)) return false;

    final ModuleWithDependentsScope moduleWithDependentsScope = (ModuleWithDependentsScope)o;

    return myModule.equals(moduleWithDependentsScope.myModule);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }
}
