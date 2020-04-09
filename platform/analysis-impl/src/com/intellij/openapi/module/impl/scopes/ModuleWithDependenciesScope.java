// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class ModuleWithDependenciesScope extends GlobalSearchScope {
  public static final int COMPILE_ONLY = 0x01;
  public static final int LIBRARIES = 0x02;
  public static final int MODULES = 0x04;
  public static final int TESTS = 0x08;
  public static final int CONTENT = 0x20;

  @MagicConstant(flags = {COMPILE_ONLY, LIBRARIES, MODULES, TESTS, CONTENT})
  @interface ScopeConstant {}

  private final Module myModule;
  @ScopeConstant
  private final int myOptions;
  private final ProjectFileIndexImpl myProjectFileIndex;

  private volatile Set<Module> myModules;
  private final TObjectIntHashMap<VirtualFile> myRoots = new TObjectIntHashMap<>();

  ModuleWithDependenciesScope(@NotNull Module module, @ScopeConstant int options) {
    super(module.getProject());
    myModule = module;
    myOptions = options;
    myProjectFileIndex = (ProjectFileIndexImpl)ProjectRootManager.getInstance(module.getProject()).getFileIndex();

    Set<VirtualFile> roots = new LinkedHashSet<>();
    if (hasOption(CONTENT)) {
      Set<Module> modules = calcModules();
      myModules = new THashSet<>(modules);
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
    if (hasOption(COMPILE_ONLY)) en.exportedOnly().compileOnly();
    if (!hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk();
    if (!hasOption(MODULES)) en.withoutDepModules();
    if (!hasOption(TESTS)) en.productionOnly();
    return en;
  }

  @NotNull
  private Set<Module> calcModules() {
    // In the case that hasOption(CONTENT), the order of the modules set matters for
    // ordering the content roots, so use a LinkedHashSet
    Set<Module> modules = new LinkedHashSet<>();
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
    return hasOption(COMPILE_ONLY) ? IndexingBundle.message("search.scope.module", myModule.getName())
                                   : IndexingBundle.message("search.scope.module.runtime", myModule.getName());
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    Set<Module> allModules = myModules;
    if (allModules == null) {
      myModules = allModules = new THashSet<>(calcModules());
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
    DirectoryInfo info = myProjectFileIndex.getInfoForFileOrDirectory(file);
    if (hasOption(CONTENT)) {
      return myRoots.contains(ProjectFileIndexImpl.getContentRootForFile(info, file, true));
    }
    if (ProjectFileIndexImpl.isFileInContent(file, info) && myRoots.contains(ProjectFileIndexImpl.getSourceRootForFile(file, info))) {
      return true;
    }
    return myRoots.contains(ProjectFileIndexImpl.getClassRootForFile(file, info));
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
    DirectoryInfo info = myProjectFileIndex.getInfoForFileOrDirectory(file);
    VirtualFile root = ProjectFileIndexImpl.getClassRootForFile(file, info);
    return root != null ? root : ProjectFileIndexImpl.getSourceRootForFile(file, info);
  }

  @TestOnly
  public Collection<VirtualFile> getRoots() {
    @SuppressWarnings("unchecked") List<VirtualFile> result = (List)ContainerUtil.newArrayList(myRoots.keys());
    result.sort(Comparator.comparingInt(myRoots::get));
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
  public int calcHashCode() {
    return 31 * myModule.hashCode() + myOptions;
  }

  @Override
  public String toString() {
    return "Module-with-dependencies:" + myModule.getName() +
           " compile-only:" + hasOption(COMPILE_ONLY) +
           " include-libraries:" + hasOption(LIBRARIES) +
           " include-other-modules:" + hasOption(MODULES) +
           " include-tests:" + hasOption(TESTS);
  }
}