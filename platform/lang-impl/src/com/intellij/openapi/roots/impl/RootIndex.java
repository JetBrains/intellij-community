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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

class RootIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootIndex");
  private static final DirectoryInfo NULL_INFO = DirectoryInfo.createNew();

  private final Set<VirtualFile> myProjectExcludedRoots = ContainerUtil.newHashSet();
  private final Map<VirtualFile, RootInfo> myRoots = ContainerUtil.newLinkedHashMap();
  private final MultiMap<String, VirtualFile> myPackagePrefixRoots = new MultiMap<String, VirtualFile>() {
    @Override
    protected Collection<VirtualFile> createCollection() {
      return ContainerUtil.newLinkedHashSet();
    }
  };

  private final Map<String, List<VirtualFile>> myDirectoriesByPackageNameCache = ContainerUtil.newConcurrentMap();
  private final Map<String, List<VirtualFile>> myDirectoriesByPackageNameCacheWithLibSrc = ContainerUtil.newConcurrentMap();
  private final Map<VirtualFile, DirectoryInfo> myInfoCache = ContainerUtil.newConcurrentMap();
  private final List<JpsModuleSourceRootType<?>> myRootTypes = ContainerUtil.newArrayList();
  private final TObjectIntHashMap<JpsModuleSourceRootType<?>> myRootTypeId = new TObjectIntHashMap<JpsModuleSourceRootType<?>>();

  RootIndex(@NotNull final Project project) {
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      final ContentEntry[] contentEntries = moduleRootManager.getContentEntries();
      final VirtualFile[] contentRoots = moduleRootManager.getContentRoots();

      for (final VirtualFile contentRoot : contentRoots) {
        RootInfo info = getOrCreateRootInfo(contentRoot);
        if (info.contentRootOf == null) {
          info.contentRootOf = module;
        }
      }

      for (ContentEntry contentEntry : contentEntries) {
        for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
          RootInfo info = getOrCreateRootInfo(excludeRoot);
          info.excludedFromModule = module;
        }

        // Init module sources
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null) {
            final RootInfo info = getOrCreateRootInfo(sourceFolderRoot);
            info.rootTypeId = getRootTypeId(sourceFolder.getRootType());
            info.sourceRootOf.add(module);
            info.packagePrefix = sourceFolder.getPackagePrefix();
          }
        }
      }

      for (OrderEntry orderEntry : orderEntries) {
        // init ordered entries
        if (orderEntry instanceof ModuleOrderEntry) {
          final Module depModule = ((ModuleOrderEntry)orderEntry).getModule();
          if (depModule != null) {
            VirtualFile[] importedClassRoots = OrderEnumerator.orderEntries(depModule).exportedOnly().recursively().classes().usingCache().getRoots();
            for (VirtualFile importedClassRoot : importedClassRoots) {
              getOrCreateRootInfo(importedClassRoot).depEntries.add(orderEntry);
            }
          }
          for (VirtualFile sourceRoot : orderEntry.getFiles(OrderRootType.SOURCES)) {
            getOrCreateRootInfo(sourceRoot).depEntries.add(orderEntry);
          }
        }
        else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          final VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          // Init library sources
          for (final VirtualFile sourceRoot : sourceRoots) {
            final RootInfo info = getOrCreateRootInfo(sourceRoot);
            info.libSourceRootEntries.add(orderEntry);
            info.packagePrefix = "";
          }

          // init library classes
          for (final VirtualFile classRoot : classRoots) {
            final RootInfo info = getOrCreateRootInfo(classRoot);
            info.libClassRootEntries.add(orderEntry);
            info.packagePrefix = "";
          }

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (VirtualFile root : ((LibraryEx)library).getExcludedRoots()) {
                getOrCreateRootInfo(root).excludedFromLibraries.add(library);
              }
              for (VirtualFile root : sourceRoots) {
                getOrCreateRootInfo(root).sourceOfLibraries.add(library);
              }
              for (VirtualFile root : classRoots) {
                getOrCreateRootInfo(root).classOfLibraries.add(library);
              }
            }
          }

        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, project)) {
      for (VirtualFile root : policy.getExcludeRootsForProject()) {
        getOrCreateRootInfo(root).excludedFromProject = true;
      }
    }

    for (RootInfo info : myRoots.values()) {
      List<RootInfo> hierarchy = getHierarchy(info);
      Pair<DirectoryInfo, String> pair = hierarchy == null ? new Pair<DirectoryInfo, String>(null, null) : calcDirectoryInfo(info, hierarchy);
      cacheInfos(info.root, info.root, pair.first);
      myPackagePrefixRoots.putValue(pair.second, info.root);
      if (shouldMarkAsProjectExcluded(info, hierarchy)) {
        myProjectExcludedRoots.add(info.root);
      }
    }
  }

  private static boolean shouldMarkAsProjectExcluded(RootInfo info, List<RootInfo> hierarchy) {
    if (hierarchy == null) return false;
    if (!info.excludedFromProject && info.excludedFromModule == null) return false;
    return ContainerUtil.find(hierarchy, new Condition<RootInfo>() {
      @Override
      public boolean value(RootInfo info) {
        return info.contentRootOf != null;
      }
    }) == null;
  }

  public void checkConsistency() {
    for (VirtualFile file : myProjectExcludedRoots) {
      assert file.exists() : file.getPath() + " does not exist";
    }

    for (VirtualFile file : myRoots.keySet()) {
      assert file.exists() : file.getPath() + " does not exist";
    }

    for (VirtualFile file : myPackagePrefixRoots.values()) {
      assert file.exists() : file.getPath() + " does not exist";
    }
  }

  private int getRootTypeId(JpsModuleSourceRootType<?> rootType) {
    if (myRootTypeId.containsKey(rootType)) {
      return myRootTypeId.get(rootType);
    }

    int id = myRootTypes.size();
    if (id > DirectoryInfo.MAX_ROOT_TYPE_ID) {
      LOG.error("Too many different types of module source roots (" + id  + ") registered: " + myRootTypes);
    }
    myRootTypes.add(rootType);
    myRootTypeId.put(rootType, id);
    return id;
  }


  @NotNull
  private RootInfo getOrCreateRootInfo(VirtualFile root) {
    RootInfo info = myRoots.get(root);
    if (info == null) {
      myRoots.put(root, info = new RootInfo(root));
    }
    return info;
  }

  @Nullable 
  public DirectoryInfo getInfoForDirectory(@NotNull final VirtualFile dir) {
    if (!dir.isValid()) {
      return null;
    }
    if (!dir.isDirectory()) {
      return myInfoCache.get(dir);
    }

    int count = 0;
    for (VirtualFile root = dir; root != null; root = root.getParent()) {
      if (++count > 1000) {
        throw new IllegalStateException("Possible loop in tree, started at " + dir.getName());
      }
      DirectoryInfo info = myInfoCache.get(root);
      if (info != null) {
        if (dir != root) {
          cacheInfos(dir, root, info);
        }
        return info == NULL_INFO ? null : info;
      }

      if (FileTypeManager.getInstance().isFileIgnored(root)) {
        return cacheInfos(dir, root, null);
      }
    }

    return cacheInfos(dir, null, null);
  }

  @Nullable
  DirectoryInfo cacheInfos(VirtualFile dir, @Nullable VirtualFile stopAt, @Nullable DirectoryInfo info) {
    while (dir != null) {
      myInfoCache.put(dir, info == null ? NULL_INFO : info);
      if (dir.equals(stopAt)) {
        break;
      }
      dir = dir.getParent();
    }
    return info;
  }

  public boolean isProjectExcludeRoot(@NotNull final VirtualFile dir) {
    return myProjectExcludedRoots.contains(dir);
  }

  @NotNull
  List<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName, final boolean includeLibrarySources) {
    Map<String, List<VirtualFile>> cacheMap = includeLibrarySources ? 
                                              myDirectoriesByPackageNameCacheWithLibSrc : 
                                              myDirectoriesByPackageNameCache;
    final List<VirtualFile> cachedResult = cacheMap.get(packageName);
    if (cachedResult != null) {
      return cachedResult;
    }

    final ArrayList<VirtualFile> result = ContainerUtil.newArrayList();

    if (StringUtil.isNotEmpty(packageName) && !packageName.startsWith(".")) {
      String parentPackage = StringUtil.getPackageName(packageName);
      String shortName = StringUtil.getShortName(packageName);
      for (VirtualFile parentDir : getDirectoriesByPackageName(parentPackage, includeLibrarySources)) {
        VirtualFile child = parentDir.findChild(shortName);
        if (isValidPackageDirectory(includeLibrarySources, child) && child.isDirectory() && packageName.equals(getPackageName(child))) {
          result.add(child);
        }
      }
    }

    Collection<VirtualFile> packagePrefixRoots = myPackagePrefixRoots.get(packageName);
    if (!packagePrefixRoots.isEmpty()) {
      for (VirtualFile file : packagePrefixRoots) {
        if (isValidPackageDirectory(includeLibrarySources, file)) {
          result.add(file);
        }
      }
    }

    if (!result.isEmpty()) {
      cacheMap.put(packageName, result);
    }
    return result;
  }

  @Contract("_,null->false")
  private boolean isValidPackageDirectory(boolean includeLibrarySources, @Nullable VirtualFile file) {
    if (file != null) {
      DirectoryInfo info = getInfoForDirectory(file);
      if (info != null) {
        if (includeLibrarySources || !info.isInLibrarySource() || info.isInModuleSource() || info.hasLibraryClassRoot()) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public String getPackageName(@NotNull final VirtualFile dir) {
    if (dir.isDirectory()) {
      if (FileTypeManager.getInstance().isFileIgnored(dir)) {
        return null;
      }

      for (final Map.Entry<String, Collection<VirtualFile>> entry : myPackagePrefixRoots.entrySet()) {
        if (entry.getValue().contains(dir)) {
          return entry.getKey();
        }
      }

      final VirtualFile parent = dir.getParent();
      if (parent != null) {
        return getPackageNameForSubdir(getPackageName(parent), dir.getName());
      }
    }

    return null;
  }

  @Nullable
  protected static String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.isEmpty() ? subdirName : parentPackageName + "." + subdirName;
  }

  @Nullable
  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo directoryInfo) {
    return myRootTypes.get(directoryInfo.getSourceRootTypeId());
  }

  boolean handleAfterEvent(List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file == null || file.isDirectory()) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static RootInfo findModuleRootInfo(List<RootInfo> hierarchy) {
    for (RootInfo info : hierarchy) {
      if (info.contentRootOf != null && info.excludedFromModule != info.contentRootOf) {
        return info;
      }
      if (info.excludedFromProject || info.excludedFromModule != null || !info.root.isDirectory()) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static RootInfo findLibraryRootInfo(List<RootInfo> hierarchy, boolean source) {
    Set<Library> excludedFromLibraries = ContainerUtil.newHashSet();
    for (RootInfo info : hierarchy) {
      excludedFromLibraries.addAll(info.excludedFromLibraries);
      if (source && !info.libSourceRootEntries.isEmpty() && 
          (info.sourceOfLibraries.isEmpty() || !excludedFromLibraries.containsAll(info.sourceOfLibraries))) {
        return info;
      } else if (!source && !info.libClassRootEntries.isEmpty() && 
                 (info.classOfLibraries.isEmpty() || !excludedFromLibraries.containsAll(info.classOfLibraries))) {
        return info;
      }
    }
    return null;
  }

  @NotNull
  private static Pair<DirectoryInfo, String> calcDirectoryInfo(RootInfo info, @NotNull final List<RootInfo> hierarchy) {
    RootInfo moduleContentInfo = findModuleRootInfo(hierarchy);
    RootInfo libraryClassInfo = findLibraryRootInfo(hierarchy, false);
    RootInfo librarySourceInfo = findLibraryRootInfo(hierarchy, true);
    if (moduleContentInfo == null && libraryClassInfo == null && librarySourceInfo == null) {
      return new Pair<DirectoryInfo, String>(null, null);
    }

    RootInfo sourceRootInfo = findPackageRootInfo(hierarchy, moduleContentInfo, null, librarySourceInfo);
    VirtualFile sourceRoot = sourceRootInfo != null ? sourceRootInfo.root : null;
    
    RootInfo moduleSourceRootInfo = findPackageRootInfo(hierarchy, moduleContentInfo, null, null);
    boolean inModuleSources = moduleSourceRootInfo != null;
    boolean inLibrarySource = librarySourceInfo != null;
    int rootTypeId = moduleSourceRootInfo != null ? moduleSourceRootInfo.rootTypeId : 0;

    OrderEntry[] entries = getOrderEntries(hierarchy, moduleContentInfo, libraryClassInfo, librarySourceInfo);
    DirectoryInfo directoryInfo = new DirectoryInfo(moduleContentInfo != null ? moduleContentInfo.contentRootOf : null,
                                                    moduleContentInfo != null ? moduleContentInfo.root : null,
                                                    sourceRoot,
                                                    libraryClassInfo != null ? libraryClassInfo.root : null,
                                                    (byte)DirectoryInfo.createSourceRootTypeData(inModuleSources, inLibrarySource, rootTypeId),
                                                    entries);

    String packagePrefix = calcPackagePrefix(info, hierarchy, moduleContentInfo, libraryClassInfo, librarySourceInfo);
    
    return Pair.create(directoryInfo, packagePrefix);
  }

  private static String calcPackagePrefix(RootInfo info,
                                          List<RootInfo> hierarchy,
                                          RootInfo moduleContentInfo,
                                          RootInfo libraryClassInfo, RootInfo librarySourceInfo) {
    RootInfo packageRootInfo = findPackageRootInfo(hierarchy, moduleContentInfo, libraryClassInfo, librarySourceInfo);
    String packagePrefix = packageRootInfo != null ? packageRootInfo.packagePrefix : null;
    if (packagePrefix != null && packageRootInfo != info) {
      String relative = VfsUtilCore.getRelativePath(info.root, packageRootInfo.root, '.');
      packagePrefix = StringUtil.isEmpty(packagePrefix) ? relative : packagePrefix + '.' + relative;
    }
    return packagePrefix;
  }

  @Nullable
  private List<RootInfo> getHierarchy(RootInfo info) {
    VirtualFile dir = info.root;
    List<RootInfo> hierarchy = ContainerUtil.newArrayList();
    while (dir != null) {
      if (FileTypeManager.getInstance().isFileIgnored(dir)) {
        return null;
      }
      ContainerUtil.addIfNotNull(hierarchy, myRoots.get(dir));
      dir = dir.getParent();
    }
    return hierarchy;
  }

  @Nullable
  private static RootInfo findPackageRootInfo(List<RootInfo> hierarchy,
                                              RootInfo moduleContentInfo,
                                              RootInfo libraryClassInfo, 
                                              RootInfo librarySourceInfo) {
    for (RootInfo info : hierarchy) {
      if (moduleContentInfo != null && info.sourceRootOf.contains(moduleContentInfo.contentRootOf) && librarySourceInfo == null) {
        return info;
      }
      if (info == libraryClassInfo || info == librarySourceInfo) {
        return info;
      }
      if (info == moduleContentInfo && info.sourceRootOf.isEmpty() && librarySourceInfo == null && libraryClassInfo == null) {
        return null;
      }
    }
    return null;
  }
  
  private static LinkedHashSet<OrderEntry> getDependencyOrderEntries(List<RootInfo> hierarchy) {
    LinkedHashSet<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
    for (RootInfo info : hierarchy) {
      orderEntries.addAll(info.depEntries);
    }
    return orderEntries;
  }
  
  private static LinkedHashSet<OrderEntry> getLibraryOrderEntries(List<RootInfo> hierarchy,
                                                                  @Nullable RootInfo libraryClassInfo,
                                                                  @Nullable RootInfo librarySourceInfo) {
    LinkedHashSet<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
    for (RootInfo info : hierarchy) {
      if (info == libraryClassInfo && info.sourceRootOf.isEmpty()) {
        orderEntries.addAll(info.libClassRootEntries);
      }
      if (info == librarySourceInfo && libraryClassInfo == null) {
        orderEntries.addAll(info.libSourceRootEntries);
      }
      if (!info.libClassRootEntries.isEmpty() || !info.sourceRootOf.isEmpty() && librarySourceInfo == null) {
        break;
      }
    }
    return orderEntries;
  }

  @Nullable
  private static ModuleSourceOrderEntry getModuleSourceEntry(List<RootInfo> hierarchy, @NotNull RootInfo moduleContentInfo) {
    for (RootInfo info : hierarchy) {
      if (info.sourceRootOf.contains(moduleContentInfo.contentRootOf)) {
        return ContainerUtil.findInstance(ModuleRootManager.getInstance(moduleContentInfo.contentRootOf).getOrderEntries(), ModuleSourceOrderEntry.class);
      }
      if (!info.libClassRootEntries.isEmpty()) {
        return null;
      }
    }
    return null;
  }

  private static OrderEntry[] getOrderEntries(List<RootInfo> hierarchy,
                                              @Nullable RootInfo moduleContentInfo,
                                              @Nullable RootInfo libraryClassInfo,
                                              @Nullable RootInfo librarySourceInfo) {
    Set<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
    orderEntries.addAll(getLibraryOrderEntries(hierarchy, libraryClassInfo, librarySourceInfo));
    orderEntries.addAll(getDependencyOrderEntries(hierarchy));
    if (moduleContentInfo != null) {
      ContainerUtil.addIfNotNull(orderEntries, getModuleSourceEntry(hierarchy, moduleContentInfo));
    }
    if (orderEntries.isEmpty()) {
      return null;
    }

    OrderEntry[] array = orderEntries.toArray(new OrderEntry[orderEntries.size()]);
    Arrays.sort(array, DirectoryInfo.BY_OWNER_MODULE);
    return array;
  }

  private static class RootInfo {
    final VirtualFile root;
    Module contentRootOf;
    Set<Module> sourceRootOf = new LinkedHashSet<Module>(1);
    int rootTypeId;
    Set<OrderEntry> libClassRootEntries = new LinkedHashSet<OrderEntry>(1);
    Set<OrderEntry> libSourceRootEntries = new LinkedHashSet<OrderEntry>(1);
    Set<OrderEntry> depEntries = new LinkedHashSet<OrderEntry>();
    Set<Library> excludedFromLibraries = new HashSet<Library>(1);
    Set<Library> classOfLibraries = new HashSet<Library>(1);
    Set<Library> sourceOfLibraries = new HashSet<Library>(1);
    boolean excludedFromProject;
    Module excludedFromModule;
    String packagePrefix;

    RootInfo(VirtualFile root) {
      this.root = root;
    }
    
  }

}