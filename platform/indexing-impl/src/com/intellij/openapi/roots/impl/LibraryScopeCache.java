/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SdkResolveScopeProvider;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yole
 */
public class LibraryScopeCache {
  public static LibraryScopeCache getInstance(Project project) {
    return ServiceManager.getService(project, LibraryScopeCache.class);
  }

  private final Project myProject;
  private final ConcurrentMap<Module[], GlobalSearchScope> myLibraryScopes = ContainerUtil.newConcurrentMap(
    new TObjectHashingStrategy<Module[]>() {
      @Override
      public int computeHashCode(Module[] object) {
        return Arrays.hashCode(object);
      }

      @Override
      public boolean equals(Module[] o1, Module[] o2) {
        return Arrays.equals(o1, o2);
      }
    });
  private final ConcurrentMap<String, GlobalSearchScope> mySdkScopes = ContainerUtil.newConcurrentMap();
  private final Map<List<OrderEntry>, GlobalSearchScope> myLibraryResolveScopeCache = new ConcurrentFactoryMap<List<OrderEntry>, GlobalSearchScope>() {
    @Nullable
    @Override
    protected GlobalSearchScope create(@NotNull List<OrderEntry> key) {
      return calcLibraryScope(key);
    }
  };


  public LibraryScopeCache(Project project) {
    myProject = project;
  }

  public void clear() {
    myLibraryScopes.clear();
    mySdkScopes.clear();
    myLibraryResolveScopeCache.clear();
  }

  @NotNull
  public GlobalSearchScope getLibrariesOnlyScope() {
    return getScopeForLibraryUsedIn(Collections.<Module>emptyList());
  }

  @NotNull
  public GlobalSearchScope getScopeForLibraryUsedIn(@NotNull List<Module> modulesLibraryIsUsedIn) {
    Set<Module> set = new THashSet<Module>(modulesLibraryIsUsedIn);
    Module[] uniques = set.toArray(new Module[set.size()]);
    Arrays.sort(uniques, new Comparator<Module>() {
      @Override
      public int compare(Module o1, Module o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    GlobalSearchScope scope = myLibraryScopes.get(uniques);
    if (scope != null) {
      return scope;
    }
    GlobalSearchScope newScope = uniques.length == 0
                                 ? new LibrariesOnlyScope(GlobalSearchScope.allScope(myProject))
                                 : new LibraryRuntimeClasspathScope(myProject, modulesLibraryIsUsedIn);
    return ConcurrencyUtil.cacheOrGet(myLibraryScopes, uniques, newScope);
  }

  /**
   * Resolve references in SDK/libraries in context of all modules which contain it, but prefer classes from the same library
   * @param orderEntries the order entries that reference a particular SDK/library
   * @return a cached resolve scope
   */
  @NotNull
  public GlobalSearchScope getLibraryScope(@NotNull List<OrderEntry> orderEntries) {
    return myLibraryResolveScopeCache.get(orderEntries);
  }

  @NotNull
  private GlobalSearchScope calcLibraryScope(@NotNull List<OrderEntry> orderEntries) {
    List<Module> modulesLibraryUsedIn = new ArrayList<Module>();

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

    GlobalSearchScope allCandidates = getScopeForLibraryUsedIn(modulesLibraryUsedIn);
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


  @NotNull
  public GlobalSearchScope getScopeForSdk(@NotNull JdkOrderEntry jdkOrderEntry) {
    final String jdkName = jdkOrderEntry.getJdkName();
    if (jdkName == null) return GlobalSearchScope.allScope(myProject);
    GlobalSearchScope scope = mySdkScopes.get(jdkName);
    if (scope == null) {
      for (SdkResolveScopeProvider provider : SdkResolveScopeProvider.EP_NAME.getExtensions()) {
        scope = provider.getScope(myProject, jdkOrderEntry);

        if (scope != null) {
          break;
        }
      }
      if (scope == null) {
        scope = new JdkScope(myProject, jdkOrderEntry);
      }
      return ConcurrencyUtil.cacheOrGet(mySdkScopes, jdkName, scope);
    }
    return scope;
  }

  private static class LibrariesOnlyScope extends GlobalSearchScope {
    private final GlobalSearchScope myOriginal;

    private LibrariesOnlyScope(@NotNull GlobalSearchScope original) {
      super(original.getProject());
      myOriginal = original;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myOriginal.contains(file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return myOriginal.compare(file1, file2);
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
