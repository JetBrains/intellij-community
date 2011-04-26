/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModuleWithDependenciesScope extends GlobalSearchScope {
  private final Module myModule;

  private final boolean myCompileClasspath;
  private final boolean myIncludeLibraries;
  private final boolean myIncludeOtherModules;
  private final boolean myIncludeTests;

  private final ProjectFileIndex myProjectFileIndex;

  private final Set<Module> myModules = new LinkedHashSet<Module>();
  private final Set<VirtualFile> myRoots = new LinkedHashSet<VirtualFile>();

  public ModuleWithDependenciesScope(Module module,
                                     boolean compileClasspath,
                                     boolean includeLibraries,
                                     boolean includeOtherModules,
                                     boolean includeTests
                                     ) {
    this(module, compileClasspath, includeLibraries, includeOtherModules, includeTests, !compileClasspath);
  }

  // todo refactor to use builder-style tuning like for OrderEnumerator
  public ModuleWithDependenciesScope(Module module,
                                     boolean compileClasspath,
                                     boolean includeLibraries,
                                     boolean includeOtherModules,
                                     boolean includeTests,
                                     boolean runtimeClasspath) {
    super(module.getProject());
    myModule = module;

    myCompileClasspath = compileClasspath;
    myIncludeLibraries = includeLibraries;
    myIncludeOtherModules = includeOtherModules;
    myIncludeTests = includeTests;

    myProjectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();

    OrderEnumerator en = ModuleRootManager.getInstance(module).orderEntries();
    /*if (myIncludeOtherModules) */en.recursively();

    if (myCompileClasspath) {
      en.exportedOnly().compileOnly();
    }
    if (runtimeClasspath) {
      en.runtimeOnly();
    }
    if (!myIncludeLibraries) en.withoutLibraries().withoutSdk();
    if (!myIncludeOtherModules) en.withoutDepModules();
    if (!myIncludeTests) en.productionOnly();

    en.forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry each) {
        if (each instanceof ModuleOrderEntry) {
          myModules.add(((ModuleOrderEntry)each).getModule());
        }
        else if (each instanceof ModuleSourceOrderEntry) {
          myModules.add(each.getOwnerModule());
        }
        return true;
      }
    });

    Collections.addAll(myRoots, en.roots(new NotNullFunction<OrderEntry, OrderRootType>() {
      @NotNull
      @Override
      public OrderRootType fun(OrderEntry entry) {
        if (entry instanceof ModuleOrderEntry || entry instanceof ModuleSourceOrderEntry) return OrderRootType.SOURCES;
        return OrderRootType.CLASSES;
      }
    }).getRoots());
  }

  @Override
  public String getDisplayName() {
    return myCompileClasspath ? PsiBundle.message("search.scope.module", myModule.getName())
                              : PsiBundle.message("search.scope.module.runtime", myModule.getName());
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myModules.contains(aModule);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule) && (myIncludeTests || !testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  @Override
  public boolean contains(VirtualFile file) {
    if (myProjectFileIndex.isInContent(file) && myRoots.contains(myProjectFileIndex.getSourceRootForFile(file))) {
      return true;
    }
    return myRoots.contains(myProjectFileIndex.getClassRootForFile(file));
  }

  @Override
  public int compare(VirtualFile file1, VirtualFile file2) {
    VirtualFile r1 = getFileRoot(file1);
    VirtualFile r2 = getFileRoot(file2);
    if (r1 == r2) return 0;

    if (r1 == null) return -1;
    if (r2 == null) return 1;

    for (VirtualFile root : myRoots) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
    }
    return 0;
  }

  @Nullable
  private VirtualFile getFileRoot(VirtualFile file) {
    if (myProjectFileIndex.isInContent(file)) {
      return myProjectFileIndex.getSourceRootForFile(file);
    }
    return myProjectFileIndex.getClassRootForFile(file);
  }

  @TestOnly
  public Collection<VirtualFile> getRoots() {
    return Collections.unmodifiableSet(myRoots);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ModuleWithDependenciesScope that = (ModuleWithDependenciesScope)o;

    if (myCompileClasspath != that.myCompileClasspath) return false;
    if (myIncludeLibraries != that.myIncludeLibraries) return false;
    if (myIncludeOtherModules != that.myIncludeOtherModules) return false;
    if (myIncludeTests != that.myIncludeTests) return false;
    if (myModule != null ? !myModule.equals(that.myModule) : that.myModule != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myModule != null ? myModule.hashCode() : 0;
    result = 31 * result + (myIncludeLibraries ? 1 : 0);
    result = 31 * result + (myIncludeOtherModules ? 1 : 0);
    result = 31 * result + (myIncludeTests ? 1 : 0);
    result = 31 * result + (myCompileClasspath ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Module with dependencies:" + myModule.getName() +
           " compile:" + myCompileClasspath +
           " include libraries:" + myIncludeLibraries +
           " include other modules:" + myIncludeOtherModules +
           " include tests:" + myIncludeTests;
  }
}
