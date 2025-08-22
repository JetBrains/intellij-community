// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.ModuleContext;
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkContext;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.*;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.indexing.IndexingBundle;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class ModuleWithDependenciesScope extends GlobalSearchScope implements VirtualFileEnumerationAware,
                                                                                    CodeInsightContextAwareSearchScope,
                                                                                    ActualCodeInsightContextInfo {

  private static final Logger LOG = Logger.getInstance(ModuleWithDependenciesScope.class);

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

  private volatile Set<Module> myModules; // lazy calculated, use `getModules()` instead!
  private final RootContainer myRoots;
  private final SingleFileSourcesTracker mySingleFileSourcesTracker;

  ModuleWithDependenciesScope(@NotNull Module module, @ScopeConstant int options) {
    super(module.getProject());
    myModule = module;
    myOptions = options;
    myProjectFileIndex = (ProjectFileIndexImpl)ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    if (isSharedSourceSupportEnabled()) {
      myRoots = new MultiverseRootContainer(calcRootsMultiverse());
    }
    else {
      myRoots = new ClassicRootContainer(calcRoots());
    }
    mySingleFileSourcesTracker = SingleFileSourcesTracker.getInstance(module.getProject());
  }

  private @NotNull Object2IntMap<VirtualFile> calcRoots() {
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

  private @NotNull Map<VirtualFile, ScopeRootDescriptor> calcRootsMultiverse() {
    OrderRootsEnumerator en = getOrderEnumeratorForOptions().roots(entry -> {
      if (entry instanceof ModuleOrderEntry || entry instanceof ModuleSourceOrderEntry) return OrderRootType.SOURCES;
      return OrderRootType.CLASSES;
    });
    Collection<RootEntry> entries = en.getRootEntries();

    int i = 1;
    Map<VirtualFile, ScopeRootDescriptor> map = new HashMap<>(entries.size());
    for (RootEntry root : entries) {
      map.put(root.root(), ScopeRootDescriptors.ScopeRootDescriptor(root.root(), root.orderEntry(), i++));
    }
    return map;
  }


  private @NotNull OrderEnumerator getOrderEnumeratorForOptions() {
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
      if (each instanceof ModuleOrderEntry moduleOrderEntry) {
        ContainerUtil.addIfNotNull(modules, moduleOrderEntry.getModule());
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
    Set<Module> allModules = getModules();
    return allModules.contains(aModule);
  }

  private @NotNull Set<Module> getModules() {
    Set<Module> allModules = myModules;
    if (allModules == null) {
      myModules = allModules = new HashSet<>(calcModules());
    }
    return allModules;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule) && (hasOption(TESTS) || !testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return hasOption(LIBRARIES);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return this;
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file) {
     //in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, myModule)) {
      // todo IJPL-339 support bazel in search scopes???
      return CodeInsightContextAwareSearchScopes.NoContextFileInfo();
    }

    Collection<RootDescriptor> roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file);
    if (roots.isEmpty()) return CodeInsightContextAwareSearchScopes.DoesNotContainFileInfo();

    Set<CodeInsightContext> result = new SmartHashSet<>();
    for (RootDescriptor rootDescriptor : roots) {
      ScopeRootDescriptor descriptor = myRoots.getRootDescriptor(rootDescriptor);
      if (descriptor != null) {
        CodeInsightContext context = convertToContext(descriptor);
        if (context != null) {
          result.add(context);
        }
      }
    }
    return CodeInsightContextAwareSearchScopes.createContainingContextFileInfo(result);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    // in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, myModule)) return true;

    if (isSharedSourceSupportEnabled()) {
      Collection<RootDescriptor> roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file);
      return ContainerUtil.exists(roots, root -> myRoots.getRootDescriptor(root) != null);
    }
    else {
      VirtualFile root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file);
      return root != null && myRoots.containsRoot(root);
    }
  }

  @ApiStatus.Experimental
  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    if (!isSharedSourceSupportEnabled()) {
      return contains(file);
    }

    // in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, myModule)) {
      // todo IJPL-339 is it correct???
      if (context instanceof ModuleContext moduleContext && moduleContext.getModule() == myModule) {
        return true;
      }
    }

    RootDescriptor rootDescriptor = convertContextToRootDescriptor(file, context);
    if (rootDescriptor == null) return false;

    VirtualFile root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file);
    if (root == null) return false;

    ScopeRootDescriptor existingDescriptor = myRoots.getRootDescriptor(root);
    if (existingDescriptor == null) return false;

    boolean result = existingDescriptor.correspondTo(rootDescriptor);

    // don't change order of checks!
    if (!result && LOG.isDebugEnabled() && contains(file)) {
      LOG.debug("File " + file + " is in scope, but not with " + context);
    }

    return result;
  }

  private static @Nullable RootDescriptor convertContextToRootDescriptor(
    @NotNull VirtualFile root,
    @NotNull CodeInsightContext context
  ) {
    if (context instanceof ModuleContext moduleContext) {
      Module module = moduleContext.getModule();
      if (module == null) return null;
      return new ModuleRootDescriptor(root, module);
    }

    if (context instanceof LibraryContext libraryContext) {
      Library library = libraryContext.getLibrary();
      if (library == null) return null;
      return new LibraryRootDescriptor(root, library);
    }

    if (context instanceof SdkContext sdkContext) {
      Sdk sdk = sdkContext.getSdk();
      if (sdk == null) return null;
      return new SdkRootDescriptor(root, sdk);
    }

    return null;
  }

  private @Nullable CodeInsightContext convertToContext(@NotNull ScopeRootDescriptor descriptor) {
    OrderEntry entry = descriptor.getOrderEntry();
    if (entry instanceof ModuleSourceOrderEntry moduleSourceOrderEntry) {
      Module module = moduleSourceOrderEntry.getRootModel().getModule();
      ProjectModelContextBridge bridge = ProjectModelContextBridge.getInstance(myModule.getProject());
      return bridge.getContext(module);
    }

    if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
      Library library = libraryOrderEntry.getLibrary();
      if (library == null) return null;
      ProjectModelContextBridge bridge = ProjectModelContextBridge.getInstance(myModule.getProject());
      return bridge.getContext(library);
    }

    if (entry instanceof JdkOrderEntry jdkOrderEntry) {
      Sdk sdk = jdkOrderEntry.getJdk();
      if (sdk == null) return null;
      ProjectModelContextBridge bridge = ProjectModelContextBridge.getInstance(myModule.getProject());
      return bridge.getContext(sdk);
    }

    return null;
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    VirtualFile r1 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file1);
    VirtualFile r2 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file2);
    if (Comparing.equal(r1, r2)) return 0;

    if (r1 == null) return -1;
    if (r2 == null) return 1;

    RootContainer roots = myRoots;
    int i1 = roots.getPriority(r1);
    int i2 = roots.getPriority(r2);
    if (i1 == 0 && i2 == 0) return 0;
    if (i1 > 0 && i2 > 0) return i2 - i1;
    return i1 > 0 ? 1 : -1;
  }

  @TestOnly
  public @NotNull Collection<VirtualFile> getRoots() {
    return myRoots.getSortedRoots();
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
    Set<Module> modules = getModules();
    // todo might be not cheap
    if (myRoots.getSize() > 1 && (hasOption(MODULES) && modules.size() > 1 || hasOption(LIBRARIES))) {
      return VirtualFileEnumeration.EMPTY;
    }

    return getFileEnumerationUnderRoots(myRoots.getRoots());
  }

  private boolean isSharedSourceSupportEnabled() {
    return CodeInsightContexts.isSharedSourceSupportEnabled(Objects.requireNonNull(getProject()));
  }

  /**
   * Compute a set of ids of all files under {@code roots}
   */
  public static @NotNull VirtualFileEnumeration getFileEnumerationUnderRoots(@NotNull Collection<? extends VirtualFile> roots) {
    IntSet result = new IntOpenHashSet();
    for (VirtualFile file : roots) {
      if (file instanceof VirtualFileWithId id) {
        int[] children = VirtualFileManager.getInstance().listAllChildIds(id.getId());
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