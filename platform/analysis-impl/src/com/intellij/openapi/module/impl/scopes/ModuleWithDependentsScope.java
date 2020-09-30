// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class ModuleWithDependentsScope extends GlobalSearchScope {
  private final Set<Module> myRootModules;
  private final ProjectFileIndexImpl myProjectFileIndex;
  private final Set<Module> myModules = new HashSet<>();
  private final Set<Module> myProductionOnTestModules = new HashSet<>();

  ModuleWithDependentsScope(@NotNull Module module) {
    this(module.getProject(), Collections.singleton(module));
  }

  public ModuleWithDependentsScope(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    super(project);
    myRootModules = new LinkedHashSet<>(modules);

    myProjectFileIndex = (ProjectFileIndexImpl)ProjectRootManager.getInstance(project).getFileIndex();

    myModules.addAll(myRootModules);

    ModuleIndex index = getModuleIndex(project);

    Collection<Module> walkingQueue = new HashSetQueue<>();
    walkingQueue.addAll(myRootModules);
    for (Module current : walkingQueue) {
      Collection<Module> usages = index.allUsages.get(current);
      myModules.addAll(usages);
      walkingQueue.addAll(index.exportingUsages.get(current));

      if (myProductionOnTestModules.contains(current)) {
        myProductionOnTestModules.addAll(usages);
      }
      myProductionOnTestModules.addAll(index.productionOnTestUsages.get(current));
    }
  }

  private static final class ModuleIndex {
    final MultiMap<Module, Module> allUsages = new MultiMap<>();
    final MultiMap<Module, Module> exportingUsages = new MultiMap<>();
    final MultiMap<Module, Module> productionOnTestUsages = new MultiMap<>();
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
              index.allUsages.putValue(referenced, module);
              if (((ModuleOrderEntry)orderEntry).isExported()) {
                index.exportingUsages.putValue(referenced, module);
              }
              if (((ModuleOrderEntry)orderEntry).isProductionOnTestDependency()) {
                index.productionOnTestUsages.putValue(referenced, module);
              }
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

  boolean contains(@NotNull VirtualFile file, boolean fromTests) {
    // optimization: fewer calls to getInfoForFileOrDirectory()
    DirectoryInfo info = myProjectFileIndex.getInfoForFileOrDirectory(file);
    Module moduleOfFile = info.getModule();
    if (moduleOfFile == null || !myModules.contains(moduleOfFile)) return false;
    if (fromTests &&
        !myProductionOnTestModules.contains(moduleOfFile) &&
        !TestSourcesFilter.isTestSources(file, moduleOfFile.getProject())) {
      return false;
    }
    return ProjectFileIndexImpl.isFileInContent(file, info);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module module) {
    return myModules.contains(module);
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(Objects.requireNonNull(project));
    return myRootModules
      .stream()
      .flatMap(module -> DirectoryIndex.getInstance(project).getDependentUnloadedModules(module).stream())
      .map(moduleManager::getUnloadedModuleDescription)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Override
  @NonNls
  public String toString() {
    return "Modules with dependents:" + StringUtil.join(myRootModules, Module::getName, ",");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof ModuleWithDependentsScope && myModules.equals(((ModuleWithDependentsScope)o).myModules);
  }

  @Override
  public int calcHashCode() {
    return myModules.hashCode();
  }
}
