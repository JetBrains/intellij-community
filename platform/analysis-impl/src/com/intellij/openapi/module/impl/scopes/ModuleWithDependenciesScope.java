// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class ModuleWithDependenciesScope extends GlobalSearchScope implements VirtualFileEnumerationAware {
  public static final int COMPILE_ONLY = 0x01;
  public static final int LIBRARIES = 0x02;
  public static final int MODULES = 0x04;
  public static final int TESTS = 0x08;
  private volatile VirtualFileEnumeration myVirtualFileEnumeration;
  private volatile long myVFSModificationCount;

  @MagicConstant(flags = {COMPILE_ONLY, LIBRARIES, MODULES, TESTS})
  @interface ScopeConstant {}

  private final Module myModule;
  @ScopeConstant
  private final int myOptions;
  private final ProjectFileIndexImpl myProjectFileIndex;

  private volatile Set<Module> myModules;
  private final Object2IntMap<VirtualFile> myRoots;
  private final SingleFileSourcesTracker mySingleFileSourcesTracker;

  ModuleWithDependenciesScope(@NotNull Module module, @ScopeConstant int options) {
    super(module.getProject());
    myModule = module;
    myOptions = options;
    myProjectFileIndex = (ProjectFileIndexImpl)ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    myRoots = calcRoots();
    mySingleFileSourcesTracker = SingleFileSourcesTracker.getInstance(module.getProject());
  }

  @NotNull
  private Object2IntMap<VirtualFile> calcRoots() {
    OrderRootsEnumerator en = getOrderEnumeratorForOptions().roots(entry -> {
      if (entry instanceof ModuleOrderEntry || entry instanceof ModuleSourceOrderEntry) return OrderRootType.SOURCES;
      return OrderRootType.CLASSES;
    });
    Set<VirtualFile> roots = new LinkedHashSet<>();
    Collections.addAll(roots, en.getRoots());

    int i = 1;
    Object2IntMap<VirtualFile> map = new Object2IntOpenHashMap<>(roots.size());
    for (VirtualFile root : roots) {
      map.put(root, i++);
    }
    return map;
  }

  @NotNull
  private OrderEnumerator getOrderEnumeratorForOptions() {
    OrderEnumerator en = ModuleRootManager.getInstance(myModule).orderEntries();
    en.recursively();
    if (hasOption(COMPILE_ONLY)) en.exportedOnly().compileOnly();
    if (!hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk();
    if (!hasOption(MODULES)) en.withoutDepModules();
    if (!hasOption(TESTS)) en.productionOnly();
    return en;
  }

  private @NotNull Set<Module> calcModules() {
    Set<Module> modules = new HashSet<>();
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

  public @NotNull Module getModule() {
    return myModule;
  }

  private boolean hasOption(@ScopeConstant int option) {
    return BitUtil.isSet(myOptions, option);
  }

  @Override
  public @NotNull String getDisplayName() {
    return hasOption(COMPILE_ONLY) ? IndexingBundle.message("search.scope.module", myModule.getName())
                                   : IndexingBundle.message("search.scope.module.runtime", myModule.getName());
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    Set<Module> allModules = myModules;
    if (allModules == null) {
      myModules = allModules = new HashSet<>(calcModules());
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
    // in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, myModule)) return true;

    VirtualFile root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file);
    return root != null && myRoots.containsKey(root);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    VirtualFile r1 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file1);
    VirtualFile r2 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file2);
    if (Comparing.equal(r1, r2)) return 0;

    if (r1 == null) return -1;
    if (r2 == null) return 1;

    Object2IntMap<VirtualFile> roots = myRoots;
    int i1 = roots.getInt(r1);
    int i2 = roots.getInt(r2);
    if (i1 == 0 && i2 == 0) return 0;
    if (i1 > 0 && i2 > 0) return i2 - i1;
    return i1 > 0 ? 1 : -1;
  }

  @TestOnly
  @NotNull
  public Collection<VirtualFile> getRoots() {
    List<VirtualFile> result = new ArrayList<>(myRoots.keySet());
    result.sort(Comparator.comparingInt(myRoots::getInt));
    return result;
  }

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    VirtualFileEnumeration enumeration = myVirtualFileEnumeration;
    long currentVFSStamp = VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.getModificationCount();
    if (currentVFSStamp != myVFSModificationCount) {
      myVirtualFileEnumeration = enumeration = doExtractFileEnumeration();
      myVFSModificationCount = currentVFSStamp;
    }
    return enumeration == VirtualFileEnumeration.EMPTY ? null : enumeration;
  }

  private @NotNull VirtualFileEnumeration doExtractFileEnumeration() {
    isSearchInModuleContent(myModule); // init myModules
    // todo might be not cheap
    if (myRoots.size() > 1 && (hasOption(MODULES) && myModules.size() > 1 || hasOption(LIBRARIES))) {
      return VirtualFileEnumeration.EMPTY;
    }

    return getFileEnumerationUnderRoots(myRoots.keySet());
  }

  /**
   * Compute a set of ids of all files under {@code roots}
   */
  @NotNull
  public static VirtualFileEnumeration getFileEnumerationUnderRoots(@NotNull Collection<? extends VirtualFile> roots) {
    IntSet result = new IntOpenHashSet();
    for (VirtualFile file : roots) {
      if (file instanceof VirtualFileWithId) {
        int[] children = VirtualFileManager.getInstance().listAllChildIds(((VirtualFileWithId)file).getId());
        result.addAll(IntList.of(children));
      }
    }

    return new MyVirtualFileEnumeration(result);
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

  private static class MyVirtualFileEnumeration implements VirtualFileEnumeration {
    private final @NotNull IntSet myIds;

    MyVirtualFileEnumeration(@NotNull IntSet ids) {
      myIds = ids;
    }

    @Override
    public boolean contains(int fileId) {
      return myIds.contains(fileId);
    }

    @Override
    public int @NotNull [] asArray() {
      return myIds.toArray(ArrayUtil.EMPTY_INT_ARRAY);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyVirtualFileEnumeration that = (MyVirtualFileEnumeration)o;
      return myIds.equals(that.myIds);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myIds);
    }

    @Override
    public String toString() {
      return Arrays.toString(myIds.toIntArray());
    }
  }
}