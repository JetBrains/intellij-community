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
import com.intellij.util.containers.MultiMapBasedOnSet;
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
    final RootInfo info = buildRootInfo(project);

    Set<VirtualFile> allRoots = info.getAllRoots();
    for (VirtualFile root : allRoots) {
      List<VirtualFile> hierarchy = getHierarchy(root, allRoots);
      Pair<DirectoryInfo, String> pair = hierarchy == null ? new Pair<DirectoryInfo, String>(null, null) : info.calcDirectoryInfo(root, hierarchy);
      cacheInfos(root, root, pair.first);
      myPackagePrefixRoots.putValue(pair.second, root);
      if (info.shouldMarkAsProjectExcluded(root, hierarchy)) {
        myProjectExcludedRoots.add(root);
      }
    }
  }

  private RootInfo buildRootInfo(Project project) {
    final RootInfo info = new RootInfo();
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      final ContentEntry[] contentEntries = moduleRootManager.getContentEntries();
      final VirtualFile[] contentRoots = moduleRootManager.getContentRoots();

      for (final VirtualFile contentRoot : contentRoots) {
        if (!info.contentRootOf.containsKey(contentRoot)) {
          info.contentRootOf.put(contentRoot, module);
        }
      }

      for (ContentEntry contentEntry : contentEntries) {
        for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
          info.excludedFromModule.put(excludeRoot, module);
        }

        // Init module sources
        for (final SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          final VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null) {
            info.rootTypeId.put(sourceFolderRoot, getRootTypeId(sourceFolder.getRootType()));
            info.classAndSourceRoots.add(sourceFolderRoot);
            info.sourceRootOf.putValue(sourceFolderRoot, module);
            info.packagePrefix.put(sourceFolderRoot, sourceFolder.getPackagePrefix());
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
              info.depEntries.putValue(importedClassRoot, orderEntry);
            }
          }
          for (VirtualFile sourceRoot : orderEntry.getFiles(OrderRootType.SOURCES)) {
            info.depEntries.putValue(sourceRoot, orderEntry);
          }
        }
        else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          final VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          // Init library sources
          for (final VirtualFile sourceRoot : sourceRoots) {
            info.classAndSourceRoots.add(sourceRoot);
            info.libSourceRootEntries.putValue(sourceRoot, orderEntry);
            info.packagePrefix.put(sourceRoot, "");
          }

          // init library classes
          for (final VirtualFile classRoot : classRoots) {
            info.classAndSourceRoots.add(classRoot);
            info.libClassRootEntries.putValue(classRoot, orderEntry);
            info.packagePrefix.put(classRoot, "");
          }

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (VirtualFile root : ((LibraryEx)library).getExcludedRoots()) {
                info.excludedFromLibraries.putValue(root, library);
              }
              for (VirtualFile root : sourceRoots) {
                info.sourceOfLibraries.putValue(root, library);
              }
              for (VirtualFile root : classRoots) {
                info.classOfLibraries.putValue(root, library);
              }
            }
          }

        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, project)) {
      Collections.addAll(info.excludedFromProject, policy.getExcludeRootsForProject());
    }
    return info;
  }

  public void checkConsistency() {
    for (VirtualFile file : myProjectExcludedRoots) {
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
  private static List<VirtualFile> getHierarchy(VirtualFile dir, Set<VirtualFile> allRoots) {
    List<VirtualFile> hierarchy = ContainerUtil.newArrayList();
    while (dir != null) {
      if (FileTypeManager.getInstance().isFileIgnored(dir)) {
        return null;
      }
      if (allRoots.contains(dir)) {
        hierarchy.add(dir);
      }
      dir = dir.getParent();
    }
    return hierarchy;
  }

  private static class RootInfo {
    // getDirectoriesByPackageName used to be in this order, some clients might rely on that
    @NotNull final LinkedHashSet<VirtualFile> classAndSourceRoots = ContainerUtil.newLinkedHashSet();

    @NotNull final Map<VirtualFile, Module> contentRootOf = ContainerUtil.newHashMap();
    @NotNull final MultiMap<VirtualFile, Module> sourceRootOf = MultiMapBasedOnSet.createBasedOnSet();
    @NotNull final TObjectIntHashMap<VirtualFile> rootTypeId = new TObjectIntHashMap<VirtualFile>();
    @NotNull final MultiMap<VirtualFile, OrderEntry> libClassRootEntries = MultiMap.createSmartList();
    @NotNull final MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = MultiMap.createSmartList();
    @NotNull final MultiMap<VirtualFile, OrderEntry> depEntries = MultiMap.createSmartList();
    @NotNull final MultiMap<VirtualFile, Library> excludedFromLibraries = MultiMap.createSmartList();
    @NotNull final MultiMap<VirtualFile, Library> classOfLibraries = MultiMap.createSmartList();
    @NotNull final MultiMap<VirtualFile, Library> sourceOfLibraries = MultiMap.createSmartList();
    @NotNull final Set<VirtualFile> excludedFromProject = ContainerUtil.newHashSet();
    @NotNull final Map<VirtualFile, Module> excludedFromModule = ContainerUtil.newHashMap();
    @NotNull final Map<VirtualFile, String> packagePrefix = ContainerUtil.newHashMap();

    Set<VirtualFile> getAllRoots() {
      LinkedHashSet<VirtualFile> result = ContainerUtil.newLinkedHashSet();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      return result;
    }

    private boolean shouldMarkAsProjectExcluded(VirtualFile root, @Nullable List<VirtualFile> hierarchy) {
      if (hierarchy == null) return false;
      if (!excludedFromProject.contains(root) && !excludedFromModule.containsKey(root)) return false;
      return ContainerUtil.find(hierarchy, new Condition<VirtualFile>() {
        @Override
        public boolean value(VirtualFile ancestor) {
          return contentRootOf.containsKey(ancestor);
        }
      }) == null;
    }
    
    @Nullable
    private VirtualFile findModuleRootInfo(List<VirtualFile> hierarchy) {
      for (VirtualFile root : hierarchy) {
        Module module = contentRootOf.get(root);
        Module excludedFrom = excludedFromModule.get(root);
        if (module != null && excludedFrom != module) {
          return root;
        }
        if (excludedFromProject.contains(root) || excludedFrom != null || !root.isDirectory()) {
          return null;
        }
      }
      return null;
    }

    @Nullable
    private VirtualFile findLibraryRootInfo(List<VirtualFile> hierarchy, boolean source) {
      Set<Library> librariesToIgnore = ContainerUtil.newHashSet();
      for (VirtualFile root : hierarchy) {
        librariesToIgnore.addAll(excludedFromLibraries.get(root));
        if (source && libSourceRootEntries.containsKey(root) &&
            (!sourceOfLibraries.containsKey(root) || !librariesToIgnore.containsAll(sourceOfLibraries.get(root)))) {
          return root;
        } else if (!source && libClassRootEntries.containsKey(root) &&
                   (!classOfLibraries.containsKey(root) || !librariesToIgnore.containsAll(classOfLibraries.get(root)))) {
          return root;
        }
      }
      return null;
    }

    @NotNull
    private Pair<DirectoryInfo, String> calcDirectoryInfo(VirtualFile root, @NotNull final List<VirtualFile> hierarchy) {
      VirtualFile moduleContentRoot = findModuleRootInfo(hierarchy);
      VirtualFile libraryClassRoot = findLibraryRootInfo(hierarchy, false);
      VirtualFile librarySourceRoot = findLibraryRootInfo(hierarchy, true);
      if (moduleContentRoot == null && libraryClassRoot == null && librarySourceRoot == null) {
        return new Pair<DirectoryInfo, String>(null, null);
      }

      VirtualFile sourceRoot = findPackageRootInfo(hierarchy, moduleContentRoot, null, librarySourceRoot);

      VirtualFile moduleSourceRoot = findPackageRootInfo(hierarchy, moduleContentRoot, null, null);
      boolean inModuleSources = moduleSourceRoot != null;
      boolean inLibrarySource = librarySourceRoot != null;
      int typeId = moduleSourceRoot != null ? rootTypeId.get(moduleSourceRoot) : 0;

      OrderEntry[] entries = getOrderEntries(hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);
      DirectoryInfo directoryInfo = new DirectoryInfo(contentRootOf.get(moduleContentRoot),
                                                      moduleContentRoot,
                                                      sourceRoot,
                                                      libraryClassRoot,
                                                      (byte)DirectoryInfo.createSourceRootTypeData(inModuleSources, inLibrarySource, typeId),
                                                      entries);

      String packagePrefix = calcPackagePrefix(root, hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);

      return Pair.create(directoryInfo, packagePrefix);
    }

    private String calcPackagePrefix(VirtualFile root,
                                     List<VirtualFile> hierarchy,
                                     VirtualFile moduleContentRoot,
                                     VirtualFile libraryClassRoot, VirtualFile librarySourceRoot) {
      VirtualFile packageRoot = findPackageRootInfo(hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);
      String prefix = packagePrefix.get(packageRoot);
      if (prefix != null && packageRoot != root) {
        assert packageRoot != null;
        String relative = VfsUtilCore.getRelativePath(root, packageRoot, '.');
        prefix = StringUtil.isEmpty(prefix) ? relative : prefix + '.' + relative;
      }
      return prefix;
    }

    @Nullable
    private VirtualFile findPackageRootInfo(List<VirtualFile> hierarchy,
                                            VirtualFile moduleContentRoot,
                                            VirtualFile libraryClassRoot,
                                            VirtualFile librarySourceRoot) {
      for (VirtualFile root : hierarchy) {
        if (moduleContentRoot != null &&
            sourceRootOf.get(root).contains(contentRootOf.get(moduleContentRoot)) &&
            librarySourceRoot == null) {
          return root;
        }
        if (root == libraryClassRoot || root == librarySourceRoot) {
          return root;
        }
        if (root == moduleContentRoot && !sourceRootOf.containsKey(root) && librarySourceRoot == null && libraryClassRoot == null) {
          return null;
        }
      }
      return null;
    }

    private LinkedHashSet<OrderEntry> getDependencyOrderEntries(List<VirtualFile> hierarchy) {
      LinkedHashSet<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
      for (VirtualFile root : hierarchy) {
        orderEntries.addAll(depEntries.get(root));
      }
      return orderEntries;
    }

    private LinkedHashSet<OrderEntry> getLibraryOrderEntries(List<VirtualFile> hierarchy,
                                                             @Nullable VirtualFile libraryClassRoot,
                                                             @Nullable VirtualFile librarySourceRoot) {
      LinkedHashSet<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
      for (VirtualFile root : hierarchy) {
        if (root == libraryClassRoot && !sourceRootOf.containsKey(root)) {
          orderEntries.addAll(libClassRootEntries.get(root));
        }
        if (root == librarySourceRoot && libraryClassRoot == null) {
          orderEntries.addAll(libSourceRootEntries.get(root));
        }
        if (libClassRootEntries.containsKey(root) || sourceRootOf.containsKey(root) && librarySourceRoot == null) {
          break;
        }
      }
      return orderEntries;
    }

    @Nullable
    private ModuleSourceOrderEntry getModuleSourceEntry(List<VirtualFile> hierarchy, @NotNull VirtualFile moduleContentRoot) {
      Module module = contentRootOf.get(moduleContentRoot);
      for (VirtualFile root : hierarchy) {
        if (sourceRootOf.get(root).contains(module)) {
          return ContainerUtil.findInstance(ModuleRootManager.getInstance(module).getOrderEntries(), ModuleSourceOrderEntry.class);
        }
        if (libClassRootEntries.containsKey(root)) {
          return null;
        }
      }
      return null;
    }

    private OrderEntry[] getOrderEntries(List<VirtualFile> hierarchy,
                                         @Nullable VirtualFile moduleContentRoot,
                                         @Nullable VirtualFile libraryClassRoot,
                                         @Nullable VirtualFile librarySourceRoot) {
      Set<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
      orderEntries.addAll(getLibraryOrderEntries(hierarchy, libraryClassRoot, librarySourceRoot));
      orderEntries.addAll(getDependencyOrderEntries(hierarchy));
      if (moduleContentRoot != null) {
        ContainerUtil.addIfNotNull(orderEntries, getModuleSourceEntry(hierarchy, moduleContentRoot));
      }
      if (orderEntries.isEmpty()) {
        return null;
      }

      OrderEntry[] array = orderEntries.toArray(new OrderEntry[orderEntries.size()]);
      Arrays.sort(array, DirectoryInfo.BY_OWNER_MODULE);
      return array;
    }
  }

}