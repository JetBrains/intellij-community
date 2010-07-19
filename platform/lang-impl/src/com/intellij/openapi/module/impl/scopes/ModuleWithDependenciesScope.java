/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class ModuleWithDependenciesScope extends GlobalSearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope");

  private final Module myModule;
  private final boolean myIncludeLibraries;
  private final boolean myIncludeOtherModules;
  private final boolean myIncludeTests;

  private final ModuleFileIndex myFileIndex;
  private final ProjectFileIndex myProjectFileIndex;
  private final Set<Module> myModules;

  public ModuleWithDependenciesScope(Module module, boolean includeLibraries, boolean includeOtherModules, boolean includeTests) {
    super(module.getProject());
    myModule = module;
    myIncludeLibraries = includeLibraries;
    myIncludeOtherModules = includeOtherModules;
    myIncludeTests = includeTests;

    myFileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
    myProjectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();

    if (myIncludeOtherModules) {
      myModules = new LinkedHashSet<Module>();
      myModules.add(myModule);
      Module[] dependencies = ModuleRootManager.getInstance(myModule).getDependencies(myIncludeTests);
      ContainerUtil.addAll(myModules, dependencies);
      for (Module dependency : dependencies) {
        addExportedModules(dependency);
      }
    }
    else {
      myModules = null;
    }
  }

  private void addExportedModules(Module module) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (!orderEntry.isValid()) {
        continue;
      }
      if (orderEntry instanceof ModuleOrderEntry && ((ModuleOrderEntry)orderEntry).isExported()) {
        if (!myIncludeTests) {
          final DependencyScope scope = ((ModuleOrderEntry)orderEntry).getScope();
          if (!scope.isForProductionCompile() && !scope.isForProductionRuntime()) {
            continue;
          }
        }
        Module exportedModule = ((ModuleOrderEntry)orderEntry).getModule();
        if (!myModules.contains(exportedModule)) { //could be true in case of circular dependencies
          myModules.add(exportedModule);
          addExportedModules(exportedModule);
        }
      }
    }
  }

  public boolean contains(VirtualFile file) {
    if (!myIncludeTests && myFileIndex.isInTestSourceContent(file)) return false;

    if (myModules != null) {
      final Module module = myProjectFileIndex.getModuleForFile(file);
      if (module != null) return myModules.contains(module) &&
                                 myFileIndex.getOrderEntryForFile(file) != null &&
                                 (myIncludeTests || !myProjectFileIndex.isInTestSourceContent(file));
    }

    final List<OrderEntry> entries = myFileIndex.getOrderEntriesForFile(file);
    for (OrderEntry orderEntry : entries) {
      if (myIncludeLibraries) {
        if (orderEntry instanceof LibraryOrderEntry ||
            orderEntry instanceof JdkOrderEntry) {
          if (!myProjectFileIndex.isInLibraryClasses(file)) {
            continue;
          }
        }
        if (orderEntry instanceof ExportableOrderEntry) {
          DependencyScope scope = ((ExportableOrderEntry)orderEntry).getScope();
          if (!myIncludeTests && !scope.isForProductionCompile()) {
            continue;
          }
        }
        if (myIncludeOtherModules) {
          return true;
        }
        else {
          if (!(orderEntry instanceof ModuleOrderEntry)) return true;
        }
      }
      else {
        if (myIncludeOtherModules) {
          if (orderEntry instanceof ModuleSourceOrderEntry || orderEntry instanceof ModuleOrderEntry) return true;
        }
        else {
          if (orderEntry instanceof ModuleSourceOrderEntry) return true;
        }
      }
    }

    return false;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    OrderEntry orderEntry1 = myFileIndex.getOrderEntryForFile(file1);
    LOG.assertTrue(orderEntry1 != null);
    OrderEntry orderEntry2 = myFileIndex.getOrderEntryForFile(file2);
    LOG.assertTrue(orderEntry2 != null);
    int ret = orderEntry2.compareTo(orderEntry1);
    if (ret != 0) return ret;
    //prefer file which is closer to our module
    if (myModules != null) {
      for (Module module : myModules) {
        ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        ret = fileIndex.isInContent(file1) ? fileIndex.isInContent(file2) ? 0 : 1 : fileIndex.isInContent(file2) ? -1 : 0;
        if (ret != 0) return ret;
      }
    }
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    if (myIncludeOtherModules) {
      return myModules.contains(aModule);
    }
    else {
      return aModule == myModule;
    }
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
    return isSearchInModuleContent(aModule) && (myIncludeTests || !testSources);
  }

  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleWithDependenciesScope)) return false;

    final ModuleWithDependenciesScope moduleWithDependenciesScope = (ModuleWithDependenciesScope)o;

    if (!myModule.equals(moduleWithDependenciesScope.myModule)) return false;
    if (myIncludeLibraries != moduleWithDependenciesScope.myIncludeLibraries) return false;
    if (myIncludeOtherModules != moduleWithDependenciesScope.myIncludeOtherModules) return false;
    if (myIncludeTests != moduleWithDependenciesScope.myIncludeTests) return false;

    return true;
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @NonNls
  public String toString() {
    return "Module with dependencies:" + myModule.getName() +
           " include libraries:" + myIncludeLibraries +
           " include other modules:" + myIncludeOtherModules +
           " include tests:" + myIncludeTests;
  }

  @Override
  public String getDisplayName() {
    return PsiBundle.message("psi.search.scope.module", myModule.getName());
  }
}
