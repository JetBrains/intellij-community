// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.ModuleContext;
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.*;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class ModuleWithDependentsScope extends GlobalSearchScope implements VirtualFileEnumerationAware,
                                                                                  CodeInsightContextAwareSearchScope,
                                                                                  ActualCodeInsightContextInfo {
  private final Set<Module> myRootModules;
  private final ProjectFileIndex myProjectFileIndex;
  private final Set<Module> myModules = new HashSet<>();
  private final Set<Module> myProductionOnTestModules = new HashSet<>();
  private static final Key<CachedValue<VirtualFileEnumeration>> CACHED_FILE_ID_ENUMERATIONS_KEY =
    Key.create("CACHED_FILE_ID_ENUMERATIONS");

  @VisibleForTesting
  public ModuleWithDependentsScope(@NotNull Module module) {
    this(module.getProject(), Collections.singleton(module));
  }

  public ModuleWithDependentsScope(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    super(project);
    myRootModules = new LinkedHashSet<>(modules);

    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    myModules.addAll(myRootModules);

    ModuleIndex index = getModuleIndex(project);

    Collection<Module> walkingQueue = new HashSetQueue<>();
    walkingQueue.addAll(myRootModules);
    for (Module current : walkingQueue) {
      if (current.getProject() != project) {
        throw new IllegalArgumentException(
          "All modules must belong to " + project + "; but got " + current + " from " + current.getProject());
      }
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

  private static @NotNull ModuleIndex getModuleIndex(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      ModuleIndex index = new ModuleIndex();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
            Module referenced = moduleOrderEntry.getModule();
            if (referenced != null) {
              index.allUsages.putValue(referenced, module);
              if (moduleOrderEntry.isExported()) {
                index.exportingUsages.putValue(referenced, module);
              }
              if (moduleOrderEntry.isProductionOnTestDependency()) {
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
    return contains(file, CodeInsightContexts.anyContext(), false);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return this;
  }

  @Override
  public @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file) {
    return getFileInfo(file, false);
  }

  @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file, boolean fromTests) {
    Set<Module> modulesOfFile = myProjectFileIndex.getModulesForFile(file, true);
    Collection<Module> containingModulesOfScope = ContainerUtil.intersection(myModules, modulesOfFile);
    if (containingModulesOfScope.isEmpty()) {
      return CodeInsightContextAwareSearchScopes.DoesNotContainFileInfo();
    }

    if (fromTests) {
      Collection<Module> testModuleIntersection = ContainerUtil.intersection(containingModulesOfScope, myProductionOnTestModules);
      if (testModuleIntersection.isEmpty()) {
        Project project = Objects.requireNonNull(getProject()); // project is notnull.
        if (TestSourcesFilter.isTestSources(file, project)) {
          return CodeInsightContextAwareSearchScopes.NoContextFileInfo();
        }
        else {
          return CodeInsightContextAwareSearchScopes.DoesNotContainFileInfo();
        }
      }
      else {
        return getActualContextFileInfo(testModuleIntersection);
      }
    }
    else {
      return getActualContextFileInfo(containingModulesOfScope);
    }
  }

  private @NotNull CodeInsightContextFileInfo getActualContextFileInfo(Collection<Module> testModuleIntersection) {
    Project project = Objects.requireNonNull(getProject()); // project is notnull.
    ProjectModelContextBridge bridge = ProjectModelContextBridge.getInstance(project);
    List<ModuleContext> contexts = ContainerUtil.mapNotNull(testModuleIntersection, m -> bridge.getContext(m));
    return CodeInsightContextAwareSearchScopes.createContainingContextFileInfo(contexts);
  }

  @ApiStatus.Internal
  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    return contains(file, context, false);
  }

  boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context, boolean fromTests) {
    Set<Module> modules;
    if (CodeInsightContexts.isSharedSourceSupportEnabled(Objects.requireNonNull(getProject()))) {
      if (context == CodeInsightContexts.anyContext()) {
        modules = myProjectFileIndex.getModulesForFile(file, true);
        if (modules.isEmpty()) {
          return false;
        }
      }
      else {
        if (!(context instanceof ModuleContext moduleContext)) {
          return false;
        }
        Module module = moduleContext.getModule();
        if (module == null) {
          return false;
        }
        modules = Set.of(module);
      }
    }
    else {
      Module module = myProjectFileIndex.getModuleForFile(file);
      if (module == null) {
        return false;
      }
      modules = Set.of(module);
    }

    return ContainerUtil.intersects(modules, myModules) && (!fromTests ||
                                                            ContainerUtil.intersects(modules, myProductionOnTestModules) ||
                                                            TestSourcesFilter.isTestSources(file, Objects.requireNonNull(getProject())));
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module module) {
    return myModules.contains(module);
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
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
  public @NonNls String toString() {
    return "Modules with dependents: (roots: [" +
           StringUtil.join(myRootModules, Module::getName, ", ") +
           "], including dependents: [" +
           StringUtil.join(myModules, Module::getName, ", ") +
           "])";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof ModuleWithDependentsScope moduleWithDependentsScope && myModules.equals(moduleWithDependentsScope.myModules);
  }

  @Override
  public int calcHashCode() {
    return myModules.hashCode();
  }

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    if (myModules.size() == 1) { // otherwise may be expensive
      // optimization: for self-contained module compute the set of files in its content, to make filtering in indexing faster
      Module module = myModules.iterator().next();
      CachedValueProvider<VirtualFileEnumeration> provider = () -> {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        VirtualFileEnumeration enumeration = ModuleWithDependenciesScope.getFileEnumerationUnderRoots(List.of(roots));
        return CachedValueProvider.Result.create(enumeration, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
      };
      CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(module.getProject());
      return cachedValuesManager.getCachedValue(module, CACHED_FILE_ID_ENUMERATIONS_KEY, provider, false);
    }
    return null;
  }
}
