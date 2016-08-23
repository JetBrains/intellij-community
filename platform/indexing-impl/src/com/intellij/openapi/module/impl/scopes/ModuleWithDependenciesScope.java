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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class ModuleWithDependenciesScope extends GlobalSearchScope {
  public static final int COMPILE = 0x01;
  public static final int LIBRARIES = 0x02;
  public static final int MODULES = 0x04;
  public static final int TESTS = 0x08;
  public static final int RUNTIME = 0x10;
  public static final int CONTENT = 0x20;

  @MagicConstant(flags = {COMPILE, LIBRARIES, MODULES, TESTS, RUNTIME, CONTENT})
  public @interface ScopeConstant {}

  private final Module myModule;
  @ScopeConstant
  private final int myOptions;

  private final ProjectFileIndex myProjectFileIndex;

  private volatile Set<Module> myModules;
  private final TObjectIntHashMap<VirtualFile> myRoots = new TObjectIntHashMap<>();

  public ModuleWithDependenciesScope(@NotNull Module module, @ScopeConstant int options) {
    super(module.getProject());
    myModule = module;
    myOptions = options;

    myProjectFileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();

    final LinkedHashSet<VirtualFile> roots = ContainerUtil.newLinkedHashSet();

    if (hasOption(CONTENT)) {
      Set<Module> modules = calcModules();
      myModules = ContainerUtil.newTroveSet(modules);
      for (Module m : modules) {
        for (ContentEntry entry : ModuleRootManager.getInstance(m).getContentEntries()) {
          ContainerUtil.addIfNotNull(roots, entry.getFile());
        }
      }
    }
    else {
      OrderEnumerator en = getOrderEnumeratorForOptions();
      Collections.addAll(roots, en.roots(entry -> {
        if (entry instanceof ModuleOrderEntry || entry instanceof ModuleSourceOrderEntry) return OrderRootType.SOURCES;
        return OrderRootType.CLASSES;
      }).getRoots());
    }

    int i = 1;
    for (VirtualFile root : roots) {
      myRoots.put(root, i++);
    }
  }

  private OrderEnumerator getOrderEnumeratorForOptions() {
    OrderEnumerator en = ModuleRootManager.getInstance(myModule).orderEntries();
    en.recursively();

    if (hasOption(COMPILE)) {
      en.exportedOnly().compileOnly();
    }
    if (hasOption(RUNTIME)) {
      en.runtimeOnly();
    }
    if (!hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk();
    if (!hasOption(MODULES)) en.withoutDepModules();
    if (!hasOption(TESTS)) en.productionOnly();
    return en;
  }

  @NotNull
  private Set<Module> calcModules() {
    // In the case that hasOption(CONTENT), the order of the modules set matters for
    // ordering the content roots, so use a LinkedHashSet
    final Set<Module> modules = ContainerUtil.newLinkedHashSet();
    OrderEnumerator en = getOrderEnumeratorForOptions();
    en.forEach(each -> {
      if (each instanceof ModuleOrderEntry) {
        ContainerUtil.addIfNotNull(modules, ((ModuleOrderEntry)each).getModule());
      }
      else if (each instanceof ModuleSourceOrderEntry) {
        ContainerUtil.addIfNotNull(modules, each.getOwnerModule());
      }
      return true;
    });
    return modules;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  private boolean hasOption(@ScopeConstant int option) {
    return BitUtil.isSet(myOptions, option);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return hasOption(COMPILE) ? PsiBundle.message("search.scope.module", myModule.getName())
                              : PsiBundle.message("search.scope.module.runtime", myModule.getName());
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    Set<Module> allModules = myModules;
    if (allModules == null) {
      myModules = allModules = ContainerUtil.newTroveSet(calcModules());
    }
    return allModules.contains(aModule);
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
  public boolean contains(@NotNull VirtualFile file) {
    if (hasOption(CONTENT)) {
      return myRoots.contains(myProjectFileIndex.getContentRootForFile(file));
    }
    if (myProjectFileIndex.isInContent(file) && myRoots.contains(myProjectFileIndex.getSourceRootForFile(file))) {
      return true;
    }
    return myRoots.contains(myProjectFileIndex.getClassRootForFile(file));
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    VirtualFile r1 = getFileRoot(file1);
    VirtualFile r2 = getFileRoot(file2);
    if (Comparing.equal(r1, r2)) return 0;

    if (r1 == null) return -1;
    if (r2 == null) return 1;

    int i1 = myRoots.get(r1);
    int i2 = myRoots.get(r2);
    if (i1 == 0 && i2 == 0) return 0;
    if (i1 > 0 && i2 > 0) return i2 - i1;
    return i1 > 0 ? 1 : -1;
  }

  @Nullable
  private VirtualFile getFileRoot(@NotNull VirtualFile file) {
    VirtualFile root = myProjectFileIndex.getClassRootForFile(file);
    return root != null ? root : myProjectFileIndex.getSourceRootForFile(file);
  }

  @TestOnly
  public Collection<VirtualFile> getRoots() {
    //noinspection unchecked
    List<VirtualFile> result = (List)ContainerUtil.newArrayList(myRoots.keys());
    Collections.sort(result, (o1, o2) -> myRoots.get(o1) - myRoots.get(o2));
    return result;
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
