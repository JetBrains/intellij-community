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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModuleWithDependenciesScope extends GlobalSearchScope {

  public final static int COMPILE = 0x01;
  public final static int LIBRARIES = 0x02;
  public final static int MODULES = 0x04;
  public final static int TESTS = 0x08;
  public final static int RUNTIME = 0x10;
  public final static int CONTENT = 0x20;

  private final Module myModule;
  private final int myOptions;

  private final ProjectFileIndex myProjectFileIndex;

  private final Set<Module> myModules = new LinkedHashSet<Module>();
  private final Set<VirtualFile> myRoots = new LinkedHashSet<VirtualFile>();

  public ModuleWithDependenciesScope(Module module, int options) {
    super(module.getProject());
    myModule = module;
    myOptions = options;

    myProjectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();

    OrderEnumerator en = ModuleRootManager.getInstance(module).orderEntries();
    /*if (myIncludeOtherModules) */en.recursively();

    if (hasOption(COMPILE)) {
      en.exportedOnly().compileOnly();
    }
    if (hasOption(RUNTIME)) {
      en.runtimeOnly();
    }
    if (!hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk();
    if (!hasOption(MODULES)) en.withoutDepModules();
    if (!hasOption(TESTS)) en.productionOnly();

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

    if (hasOption(CONTENT)) {
      for (Module m : myModules) {
        ContentEntry[] entries = ModuleRootManager.getInstance(m).getContentEntries();
        for (ContentEntry entry : entries) {
          ContainerUtil.addIfNotNull(entry.getFile(), myRoots);
        }
      }
    }
    else {
      Collections.addAll(myRoots, en.roots(new NotNullFunction<OrderEntry, OrderRootType>() {
        @NotNull
        @Override
        public OrderRootType fun(OrderEntry entry) {
          if (entry instanceof ModuleOrderEntry || entry instanceof ModuleSourceOrderEntry) return OrderRootType.SOURCES;
          return OrderRootType.CLASSES;
        }
      }).getRoots());
    }
  }

  private boolean hasOption(int option) {
    return (myOptions & option) != 0;
  }

  @Override
  public String getDisplayName() {
    return hasOption(COMPILE) ? PsiBundle.message("search.scope.module", myModule.getName())
                              : PsiBundle.message("search.scope.module.runtime", myModule.getName());
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myModules.contains(aModule);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule) && (hasOption(TESTS) || !testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return hasOption(LIBRARIES);
  }

  @Override
  public boolean contains(VirtualFile file) {
    if (hasOption(CONTENT)) {
      return myRoots.contains(myProjectFileIndex.getContentRootForFile(file));
    }
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
    return myOptions == that.myOptions && myModule.equals(that.myModule);
  }

  @Override
  public int hashCode() {
    return 31 * myModule.hashCode() + myOptions;
  }

  @Override
  public String toString() {
    return "Module with dependencies:" + myModule.getName() +
           " compile:" + hasOption(COMPILE) +
           " include libraries:" + hasOption(LIBRARIES) +
           " include other modules:" + hasOption(MODULES) +
           " include tests:" + hasOption(TESTS);
  }
}
