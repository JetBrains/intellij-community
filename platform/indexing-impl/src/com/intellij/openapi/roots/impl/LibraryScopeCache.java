/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SdkResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yole
 */
public class LibraryScopeCache {
  public static LibraryScopeCache getInstance(Project project) {
    return ServiceManager.getService(project, LibraryScopeCache.class);
  }

  private final Project myProject;
  private final ConcurrentMap<List<Module>, GlobalSearchScope> myLibraryScopes = new ConcurrentHashMap<List<Module>, GlobalSearchScope>();
  private final ConcurrentMap<String, GlobalSearchScope> mySdkScopes = new ConcurrentHashMap<String, GlobalSearchScope>();

  public LibraryScopeCache(Project project) {
    myProject = project;
  }

  public void clear() {
    myLibraryScopes.clear();
    mySdkScopes.clear();
  }

  public GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn) {
    GlobalSearchScope scope = myLibraryScopes.get(modulesLibraryIsUsedIn);
    if (scope != null) {
      return scope;
    }
    GlobalSearchScope newScope = modulesLibraryIsUsedIn.isEmpty()
                                 ? new LibrariesOnlyScope(GlobalSearchScope.allScope(myProject))
                                 : new LibraryRuntimeClasspathScope(myProject, modulesLibraryIsUsedIn);
    return ConcurrencyUtil.cacheOrGet(myLibraryScopes, modulesLibraryIsUsedIn, newScope);
  }

  public GlobalSearchScope getScopeForSdk(final JdkOrderEntry jdkOrderEntry) {
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

    private LibrariesOnlyScope(final GlobalSearchScope original) {
      super(original.getProject());
      myOriginal = original;
    }

    public boolean contains(@NotNull VirtualFile file) {
      return myOriginal.contains(file);
    }

    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return myOriginal.compare(file1, file2);
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }

}
