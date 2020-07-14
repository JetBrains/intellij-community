// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yole
 */
public final class LibraryScopeCache {
  private final LibrariesOnlyScope myLibrariesOnlyScope;

  public static LibraryScopeCache getInstance(@NotNull Project project) {
    return project.getService(LibraryScopeCache.class);
  }

  private final Project myProject;
  private final ConcurrentMap<Module[], GlobalSearchScope> myLibraryScopes =
    ConcurrentCollectionFactory.createMap(new TObjectHashingStrategy<Module[]>() {
      @Override
      public int computeHashCode(Module[] object) {
        return Arrays.hashCode(object);
      }

      @Override
      public boolean equals(Module[] o1, Module[] o2) {
        return Arrays.equals(o1, o2);
      }
    });
  private final ConcurrentMap<String, GlobalSearchScope> mySdkScopes = new ConcurrentHashMap<>();
  private final Map<List<? extends OrderEntry>, GlobalSearchScope> myLibraryResolveScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryScope(key));
  private final Map<List<? extends OrderEntry>, GlobalSearchScope> myLibraryUseScopeCache = ConcurrentFactoryMap.createMap(key -> calcLibraryUseScope(key));

  public LibraryScopeCache(@NotNull Project project) {
    myProject = project;
    myLibrariesOnlyScope = new LibrariesOnlyScope(GlobalSearchScope.allScope(myProject), myProject);
  }

  void clear() {
    myLibraryScopes.clear();
    mySdkScopes.clear();
    myLibraryResolveScopeCache.clear();
    myLibraryUseScopeCache.clear();
  }

  public @NotNull GlobalSearchScope getLibrariesOnlyScope() {
    return myLibrariesOnlyScope;
  }

  private @NotNull GlobalSearchScope getScopeForLibraryUsedIn(@NotNull List<? extends Module> modulesLibraryIsUsedIn) {
    Module[] array = modulesLibraryIsUsedIn.toArray(Module.EMPTY_ARRAY);
    GlobalSearchScope scope = myLibraryScopes.get(array);
    return scope != null ? scope : ConcurrencyUtil.cacheOrGet(myLibraryScopes, array,
                                                              new LibraryRuntimeClasspathScope(myProject, modulesLibraryIsUsedIn));
  }

  /**
   * Resolve references in SDK/libraries in context of all modules which contain it, but prefer classes from the same library
   * @param orderEntries the order entries that reference a particular SDK/library
   * @return a cached resolve scope
   */
  public @NotNull GlobalSearchScope getLibraryScope(@NotNull List<? extends OrderEntry> orderEntries) {
    return myLibraryResolveScopeCache.get(orderEntries);
  }

  /**
   * Returns a scope containing all modules depending on the library transitively plus all the project's libraries
   * @param orderEntries the order entries that reference a particular SDK/library
   * @return a cached use scope
   */
  public @NotNull GlobalSearchScope getLibraryUseScope(@NotNull List<? extends OrderEntry> orderEntries) {
    return myLibraryUseScopeCache.get(orderEntries);
  }

  private @NotNull GlobalSearchScope calcLibraryScope(@NotNull List<? extends OrderEntry> orderEntries) {
    List<Module> modulesLibraryUsedIn = new ArrayList<>();

    LibraryOrderEntry lib = null;
    for (OrderEntry entry : orderEntries) {
      if (entry instanceof JdkOrderEntry) {
        return getScopeForSdk((JdkOrderEntry)entry);
      }

      if (entry instanceof LibraryOrderEntry) {
        lib = (LibraryOrderEntry)entry;
        modulesLibraryUsedIn.add(entry.getOwnerModule());
      }
      else if (entry instanceof ModuleOrderEntry) {
        modulesLibraryUsedIn.add(entry.getOwnerModule());
      }
    }

    Comparator<Module> comparator = Comparator.comparing(Module::getName);
    modulesLibraryUsedIn.sort(comparator);
    List<Module> uniquesList = ContainerUtil.removeDuplicatesFromSorted(modulesLibraryUsedIn, comparator);

    GlobalSearchScope allCandidates = uniquesList.isEmpty() ? myLibrariesOnlyScope : getScopeForLibraryUsedIn(uniquesList);
    if (lib != null) {
      final LibraryRuntimeClasspathScope preferred = new LibraryRuntimeClasspathScope(myProject, lib);
      // prefer current library
      return new DelegatingGlobalSearchScope(allCandidates, preferred) {
        @Override
        public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
          boolean c1 = preferred.contains(file1);
          boolean c2 = preferred.contains(file2);
          if (c1 && !c2) return 1;
          if (c2 && !c1) return -1;

          return super.compare(file1, file2);
        }
      };
    }
    return allCandidates;
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

  private @NotNull GlobalSearchScope calcLibraryUseScope(@NotNull List<? extends OrderEntry> entries) {
    Set<Module> modulesWithLibrary = new THashSet<>(entries.size());
    Set<Module> modulesWithSdk = new THashSet<>(entries.size());
    for (OrderEntry entry : entries) {
      (entry instanceof JdkOrderEntry ? modulesWithSdk : modulesWithLibrary).add(entry.getOwnerModule());
    }
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
  }

}
