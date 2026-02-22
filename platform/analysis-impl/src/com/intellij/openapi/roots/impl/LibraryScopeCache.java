// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency;
import com.intellij.platform.workspace.jps.entities.LibraryDependency;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.LibraryRoot;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.SdkEntity;
import com.intellij.platform.workspace.jps.entities.SdkId;
import com.intellij.platform.workspace.jps.entities.SdkRoot;
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId;
import com.intellij.platform.workspace.storage.ImmutableEntityStorage;
import com.intellij.projectModel.ModuleDependenciesGraph;
import com.intellij.projectModel.ModuleDependenciesGraphService;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridges;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Service(Service.Level.PROJECT)
public final class LibraryScopeCache {
  private final LibrariesOnlyScope myLibrariesOnlyScope;

  public static LibraryScopeCache getInstance(@NotNull Project project) {
    return project.getService(LibraryScopeCache.class);
  }

  private final Project myProject;
  private final Map<List<? extends Module>, GlobalSearchScope> myLibraryScopes = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, GlobalSearchScope> mySdkScopes = new ConcurrentHashMap<>();
  @Deprecated
  private final Map<List<? extends OrderEntry>, GlobalSearchScope> myOrderEntriesToLibraryResolveScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryScope(key));
  private final Map<VirtualFile, GlobalSearchScope> myFileToLibraryResolveScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryScope(key));
  @Deprecated
  private final Map<List<? extends OrderEntry>, GlobalSearchScope> myOrderEntriesToLibraryUseScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryUseScope(key));
  private final Map<VirtualFile, GlobalSearchScope> myFileToLibraryUseScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryUseScope(key));

  public LibraryScopeCache(@NotNull Project project) {
    myProject = project;
    myLibrariesOnlyScope = new LibrariesOnlyScope(GlobalSearchScope.allScope(myProject), myProject);
  }

  @ApiStatus.Internal
  public void clear() {
    myLibraryScopes.clear();
    mySdkScopes.clear();
    myOrderEntriesToLibraryResolveScopeCache.clear();
    myFileToLibraryResolveScopeCache.clear();
    myOrderEntriesToLibraryUseScopeCache.clear();
    myFileToLibraryUseScopeCache.clear();
  }

  public @NotNull GlobalSearchScope getLibrariesOnlyScope() {
    return myLibrariesOnlyScope;
  }

  private @NotNull GlobalSearchScope getScopeForLibraryUsedIn(@NotNull List<? extends Module> modulesLibraryIsUsedIn) {
    List<? extends Module> list = List.copyOf(modulesLibraryIsUsedIn);
    return myLibraryScopes.computeIfAbsent(list, modules -> new LibraryRuntimeClasspathScope(myProject, modules));
  }

  /**
   * Resolve references in SDK/libraries in context of all modules which contain it, but prefer classes from the same library
   * @param orderEntries the order entries that reference a particular SDK/library
   * @return a cached resolve scope
   * @deprecated use {@link #getLibraryScope(VirtualFile)}
   */
  @Deprecated
  public @NotNull GlobalSearchScope getLibraryScope(@NotNull List<? extends OrderEntry> orderEntries) {
    return myOrderEntriesToLibraryResolveScopeCache.get(orderEntries);
  }
  public @NotNull GlobalSearchScope getLibraryScope(@NotNull VirtualFile virtualFile) {
    return myFileToLibraryResolveScopeCache.get(virtualFile);
  }

  /**
   * Returns a scope containing all modules depending on the library transitively plus all the project's libraries
   * @param orderEntries the order entries that reference a particular SDK/library
   * @return a cached use scope
   * @deprecated use {@link #getLibraryUseScope(VirtualFile)}
   */
  @Deprecated
  public @NotNull GlobalSearchScope getLibraryUseScope(@NotNull List<? extends OrderEntry> orderEntries) {
    return myOrderEntriesToLibraryUseScopeCache.get(orderEntries);
  }

  public @NotNull GlobalSearchScope getLibraryUseScope(@NotNull VirtualFile vFile) {
    return myFileToLibraryUseScopeCache.get(vFile);
  }

  private @NotNull GlobalSearchScope calcLibraryScope(@NotNull List<? extends OrderEntry> orderEntries) {
    List<Module> modulesLibraryUsedIn = new ArrayList<>();

    LibraryOrderEntry lib = null;
    for (OrderEntry entry : orderEntries) {
      if (entry instanceof JdkOrderEntry jdkOrderEntry) {
        return getScopeForSdk(jdkOrderEntry);
      }

      if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
        lib = libraryOrderEntry;
        modulesLibraryUsedIn.add(entry.getOwnerModule());
      }
      else if (entry instanceof ModuleOrderEntry) {
        modulesLibraryUsedIn.add(entry.getOwnerModule());
      }
    }

    return getScopeForLibrary(modulesLibraryUsedIn, lib == null ? null : new LibraryRuntimeClasspathScope(myProject, lib));
  }

  private @NotNull GlobalSearchScope calcLibraryScope(@NotNull VirtualFile virtualFile) {
    if (Registry.is("use.workspace.model.for.calculation.library.scope")) {
      var index = ProjectFileIndex.getInstance(myProject);
      var sdks = index.findContainingSdks(virtualFile);

      for (var sdk : sdks) {
        return getScopeForSdk(sdk);
      }

      var libraries = index.findContainingLibraries(virtualFile);
      var currentSnapshot = WorkspaceModel.getInstance(myProject).getCurrentSnapshot();
      List<Module> modulesLibraryUsedIn = new ArrayList<>();
      var exportedDependentsGraph = ModuleDependenciesGraphService.getInstance(myProject).getModuleDependenciesGraph();
      for (var library: libraries) {
        modulesLibraryUsedIn.addAll(findModulesWithLibraryId(library.getSymbolicId(), currentSnapshot, exportedDependentsGraph));
      }

      LibraryEntity lib = ContainerUtil.getFirstItem(libraries);
      if (lib != null) {
        var roots = lib.getRoots().stream()
          .map(LibraryRoot::getUrl)
          .map(VirtualFileUrls::getVirtualFile)
          .toArray(VirtualFile[]::new);

        return getScopeForLibrary(modulesLibraryUsedIn, new  LibraryRuntimeClasspathScope(myProject, roots));
      }
      else {
        return getScopeForLibrary(modulesLibraryUsedIn, null);
      }
    } else {
      List<OrderEntry> orderEntries = ProjectFileIndex.getInstance(myProject).getOrderEntriesForFile(virtualFile);
      return calcLibraryScope(orderEntries);
    }
  }

  public @NotNull GlobalSearchScope getScopeForSdk(@NotNull JdkOrderEntry jdkOrderEntry) {
    final String jdkName = jdkOrderEntry.getJdkName();
    if (jdkName == null) return GlobalSearchScope.allScope(myProject);
    GlobalSearchScope scope = mySdkScopes.get(jdkName);
    if (scope == null) {
      scope = new JdkScope(myProject, jdkOrderEntry);
      return ConcurrencyUtil.cacheOrGet(mySdkScopes, jdkName, scope);
    }
    return scope;
  }

  private @NotNull GlobalSearchScope getScopeForSdk(@NotNull SdkEntity sdkEntity) {
    final String name = sdkEntity.getName();
    GlobalSearchScope scope = mySdkScopes.get(name);
    if (scope == null) {
      var roots = sdkEntity.getRoots();

      var sources = roots.stream()
        .filter(root -> Objects.equals(root.getType(), SdkRootTypeId.SOURCES))
        .map(SdkRoot::getUrl)
        .map(VirtualFileUrls::getVirtualFile)
        .toArray(VirtualFile[]::new);

      var classes = roots.stream()
        .filter(root -> Objects.equals(root.getType(), SdkRootTypeId.CLASSES))
        .map(SdkRoot::getUrl)
        .map(VirtualFileUrls::getVirtualFile)
        .toArray(VirtualFile[]::new);

      scope = new JdkScope(myProject, classes, sources, sdkEntity.getName());
      return ConcurrencyUtil.cacheOrGet(mySdkScopes, name, scope);
    }
    return scope;
  }

  private GlobalSearchScope getScopeForLibrary(List<Module> modulesLibraryUsedIn, @Nullable LibraryRuntimeClasspathScope libraryScope) {
    Comparator<Module> comparator = Comparator.comparing(Module::getName);
    modulesLibraryUsedIn.sort(comparator);
    List<? extends Module> uniquesList = ContainerUtil.removeDuplicatesFromSorted(modulesLibraryUsedIn, comparator);

    GlobalSearchScope allCandidates = uniquesList.isEmpty() ? myLibrariesOnlyScope : getScopeForLibraryUsedIn(uniquesList);
    if (libraryScope != null) {
      // prefer current library
      return new DelegatingGlobalSearchScope(allCandidates, libraryScope) {
        @Override
        public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
          boolean c1 = libraryScope.contains(file1);
          boolean c2 = libraryScope.contains(file2);
          if (c1 && !c2) return 1;
          if (c2 && !c1) return -1;

          return super.compare(file1, file2);
        }
      };
    }
    return allCandidates;
  }

  private @NotNull GlobalSearchScope calcLibraryUseScope(@NotNull List<? extends OrderEntry> entries) {
    Set<Module> modulesWithLibrary = new HashSet<>(entries.size());
    Set<Module> modulesWithSdk = new HashSet<>(entries.size());
    for (OrderEntry entry : entries) {
      (entry instanceof JdkOrderEntry ? modulesWithSdk : modulesWithLibrary).add(entry.getOwnerModule());
    }
    return calcLibraryUseScope(modulesWithLibrary, modulesWithSdk);
  }

  private @NotNull GlobalSearchScope calcLibraryUseScope(@NotNull VirtualFile virtualFile) {
    if (Registry.is("use.workspace.model.for.calculation.library.scope")) {
      var index = ProjectFileIndex.getInstance(myProject);
      var currentSnapshot = WorkspaceModel.getInstance(myProject).getCurrentSnapshot();
      var sdks = index.findContainingSdks(virtualFile);
      var libraries = index.findContainingLibraries(virtualFile);

      Set<Module> modulesWithSdk = new HashSet<>();
      for (var sdk : sdks) {
        var sdkId = sdk.getSymbolicId();
        for (var module: SequencesKt.toList(currentSnapshot.referrers(sdkId, ModuleEntity.class))) {
          var moduleBridge = ModuleBridges.findModule(module, currentSnapshot);
          if (moduleBridge != null) {
            modulesWithSdk.add(moduleBridge);
          }
        }
        addModulesInheritingProjectSdk(sdkId, currentSnapshot, modulesWithSdk);
      }

      Set<Module> modulesWithLibrary = new HashSet<>();
      var exportedDependentsGraph = ModuleDependenciesGraphService.getInstance(myProject).getModuleDependenciesGraph();
      for (var library : libraries) {
        modulesWithLibrary.addAll(findModulesWithLibraryId(library.getSymbolicId(), currentSnapshot, exportedDependentsGraph));
      }
      modulesWithLibrary.addAll(index.getModulesForFile(virtualFile, false));

      return calcLibraryUseScope(modulesWithLibrary, modulesWithSdk);
    } else {
      List<? extends OrderEntry> entries = ProjectFileIndex.getInstance(myProject).getOrderEntriesForFile(virtualFile);
      return calcLibraryUseScope(entries);
    }
  }

  private @NotNull GlobalSearchScope calcLibraryUseScope(Set<Module> modulesWithLibrary, Set<Module> modulesWithSdk) {
    modulesWithSdk.removeAll(modulesWithLibrary);

    // optimisation: if the library attached to all modules (often the case with JDK) then replace the 'union of all modules' scope with just 'project'
    if (modulesWithSdk.size() + modulesWithLibrary.size() == ModuleManager.getInstance(myProject).getModules().length) {
      return GlobalSearchScope.allScope(myProject);
    }

    List<GlobalSearchScope> united = new ArrayList<>();
    if (!modulesWithSdk.isEmpty()) {
      united.add(new ModulesScope(modulesWithSdk, myProject));
      united.add(myLibrariesOnlyScope.intersectWith(new LibraryRuntimeClasspathScope(myProject, modulesWithSdk)));
    } else {
      united.add(myLibrariesOnlyScope);
    }

    if (!modulesWithLibrary.isEmpty()) {
      united.add(new ModuleWithDependentsScope(myProject, modulesWithLibrary));
    }

    return GlobalSearchScope.union(united.toArray(GlobalSearchScope.EMPTY_ARRAY));
  }

  private static final class LibrariesOnlyScope extends DelegatingGlobalSearchScope {
    private final ProjectFileIndex myIndex;

    private LibrariesOnlyScope(@NotNull GlobalSearchScope original, @NotNull Project project) {
      super(original);
      myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) && myIndex.isInLibrary(file);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public String toString() {
      return "Libraries only in (" + myBaseScope + ")";
    }
  }

  private void addModulesInheritingProjectSdk(SdkId sdkId, ImmutableEntityStorage currentSnapshot, Set<Module> result) {
    var projectSdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    if (projectSdk == null || (!projectSdk.getName().equals(sdkId.getName()) && !projectSdk.getSdkType().getName().equals(sdkId.getType()))) {
      return;
    }

    for (var moduleEntity : SequencesKt.toList(currentSnapshot.entities(ModuleEntity.class))) {
      if (ContainerUtil.exists(moduleEntity.getDependencies(), dep -> dep instanceof InheritedSdkDependency)) {
        var moduleBridge = ModuleBridges.findModule(moduleEntity, currentSnapshot);
        if (moduleBridge != null) {
          result.add(moduleBridge);
        }
      }
    }
  }

  private static Set<Module> findModulesWithLibraryId(LibraryId libraryId, ImmutableEntityStorage currentSnapshot, ModuleDependenciesGraph exportedDependentsGraph) {
    Set<ModuleEntity> modulesWithLibrary = new HashSet<>();
    var ownerModules = SequencesKt.toList(currentSnapshot.referrers(libraryId, ModuleEntity.class));

    for (var module : ownerModules) {
      modulesWithLibrary.add(module);
      if (exportsLibrary(module, libraryId)) {
        modulesWithLibrary.addAll(exportedDependentsGraph.getModuleDependants(module));
      }
    }
    return ContainerUtil.map2Set(modulesWithLibrary, (moduleEntity) -> ModuleBridges.findModule(moduleEntity, currentSnapshot));
  }

  private static boolean exportsLibrary(ModuleEntity module, LibraryId libraryId) {
    for (var moduleDependency : module.getDependencies()) {
      if (moduleDependency instanceof  LibraryDependency libraryDependency) {
        if (libraryDependency.getLibrary().equals(libraryId) && libraryDependency.getExported()) return true;
      }
    }
    return false;
  }
}
