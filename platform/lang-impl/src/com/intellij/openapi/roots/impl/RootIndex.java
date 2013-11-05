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
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

class RootIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootIndex");
  private static final DirectoryInfo NULL_INFO = DirectoryInfo.createNew();

  private final Set<VirtualFile> myProjectExcludedRoots = ContainerUtil.newHashSet();
  private final Set<VirtualFile> myLibraryExcludedRoots = ContainerUtil.newHashSet();
  private final Map<VirtualFile, DirectoryInfo> myRoots = ContainerUtil.newTroveMap();
  private final Map<String, HashSet<VirtualFile>> myPackagePrefixRoots = ContainerUtil.newHashMap();

  private final Map<String, List<VirtualFile>> myDirectoriesByPackageNameCache = ContainerUtil.newConcurrentMap();
  private final Map<String, List<VirtualFile>> myDirectoriesByPackageNameCacheWithLibSrc = ContainerUtil.newConcurrentMap();
  private final Map<VirtualFile, DirectoryInfo> myInfoCache = ContainerUtil.newConcurrentMap();
  private final List<JpsModuleSourceRootType<?>> myRootTypes = ContainerUtil.newArrayList();
  private final TObjectIntHashMap<JpsModuleSourceRootType<?>> myRootTypeId = new TObjectIntHashMap<JpsModuleSourceRootType<?>>();

  RootIndex(@NotNull final Project project) {
    final MultiMap<VirtualFile, OrderEntry> depEntries = new MultiMap<VirtualFile, OrderEntry>();
    final MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<VirtualFile, OrderEntry>();
    final MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<VirtualFile, OrderEntry>();

    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      final ContentEntry[] contentEntries = moduleRootManager.getContentEntries();
      final VirtualFile[] contentRoots = moduleRootManager.getContentRoots();

      for (final VirtualFile contentRoot : contentRoots) {
        myRoots.put(contentRoot, getOrCreateDirectoryInfo(contentRoot).with(module, contentRoot, null, null, (byte) 0, null));
      }

      for (ContentEntry contentEntry : contentEntries) {
        // Init excluded roots
        Collections.addAll(myProjectExcludedRoots, contentEntry.getExcludeFolderFiles());

        // Init module sources
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null) {
            final DirectoryInfo info = getOrCreateDirectoryInfo(sourceFolderRoot);

            VirtualFile contentRoot = null;
            for (VirtualFile root : contentRoots) {
              if (VfsUtilCore.isAncestor(root, sourceFolderRoot, false)) {
                contentRoot = root;
                break;
              }
            }

            int rootTypeId = getRootTypeId(sourceFolder.getRootType());

            int sourceRootTypeData = DirectoryInfo.createSourceRootTypeData(true, info.isInLibrarySource(), rootTypeId);
            DirectoryInfo info1 = info.with(module, contentRoot, sourceFolderRoot, null, sourceRootTypeData, null);

            myRoots.put(sourceFolderRoot, info1);
            initializePrefix(sourceFolderRoot, sourceFolder.getPackagePrefix());
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
              depEntries.putValue(importedClassRoot, orderEntry);
            }
          }
          for (VirtualFile sourceRoot : orderEntry.getFiles(OrderRootType.SOURCES)) {
            depEntries.putValue(sourceRoot, orderEntry);
          }
        }
        else if (orderEntry instanceof ModuleSourceOrderEntry) {
          final VirtualFile[] sourceRoots = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getSourceRoots();
          for (VirtualFile sourceRoot : sourceRoots) {
            fillMapWithOrderEntries(sourceRoot, Arrays.asList(orderEntry), orderEntry.getOwnerModule(), null, null);
          }
        }
        else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          final VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          // init library classes
          for (final VirtualFile classRoot : classRoots) {
            libClassRootEntries.putValue(classRoot, orderEntry);
            final DirectoryInfo info = getOrCreateDirectoryInfo(classRoot).with(null, null, null, classRoot, (byte) 0, null);
            myRoots.put(classRoot, info);
            if (!info.isInModuleSource() && !info.isInLibrarySource()) {
              initializePrefix(classRoot, "");
            }
          }

          // Init library sources
          for (final VirtualFile sourceRoot : sourceRoots) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
            final DirectoryInfo info = getOrCreateDirectoryInfo(sourceRoot);

            int sourceRootTypeData = DirectoryInfo.createSourceRootTypeData(info.isInModuleSource(), true, getRootTypeId(JavaSourceRootType.SOURCE));

            myRoots.put(sourceRoot, info.with(null, null, sourceRoot, null, sourceRootTypeData, null));
            initializePrefix(sourceRoot, "");
          }

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              Collections.addAll(myLibraryExcludedRoots, ((LibraryEx)library).getExcludedRoots());
            }
          }

        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, project)) {
      Collections.addAll(myProjectExcludedRoots, policy.getExcludeRootsForProject());
    }

    // fill ordered entries
    for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : depEntries.entrySet()) {
      fillMapWithOrderEntries(mapEntry.getKey(), mapEntry.getValue(), null, null, null);
    }
    for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
      fillMapWithOrderEntries(mapEntry.getKey(), mapEntry.getValue(), null, mapEntry.getKey(), null);
    }
    for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
      fillMapWithOrderEntries(mapEntry.getKey(), mapEntry.getValue(), null, null, mapEntry.getKey());
    }

    mergeWithParentInfos();
  }

  private void mergeWithParentInfos() {
    for (Map.Entry<VirtualFile, DirectoryInfo> entry : myRoots.entrySet()) {
      DirectoryInfo info = entry.getValue();
      Module module = info.getModule();
      VirtualFile contentRoot = info.getContentRoot();
      VirtualFile libraryClassRoot = info.getLibraryClassRoot();
      boolean inModuleSource = info.isInModuleSource();
      boolean inLibrarySource = info.isInLibrarySource();

      OrderEntry[] orderEntries = info.getOrderEntries();
      
      boolean nested = false;
      
      VirtualFile eachFile = entry.getKey().getParent();
      while (eachFile != null) {
        DirectoryInfo eachInfo = myRoots.get(eachFile);
        if (eachInfo != null) {
          nested = true;

          if (eachInfo.getLibraryClassRoot() != null && libraryClassRoot == null && !info.isInModuleSource()) {
            orderEntries = eachInfo.getOrderEntries();
          }

          if (module == null) {
            module = eachInfo.getModule();
          }
          if (libraryClassRoot == null) {
            libraryClassRoot = eachInfo.getLibraryClassRoot();
          }
          if (contentRoot == null) {
            contentRoot = eachInfo.getContentRoot();
          }
          inModuleSource |= eachInfo.isInModuleSource();
          inLibrarySource |= eachInfo.isInLibrarySource();
        }
        if (isAnyExcludeRoot(eachFile)) {
          break;
        }
        
        eachFile = eachFile.getParent();
      }
      if (nested) {
        int sourceRootTypeData = DirectoryInfo.createSourceRootTypeData(inModuleSource, inLibrarySource, info.getSourceRootTypeId());
        if (orderEntries.length == 0) orderEntries = null;
        entry.setValue(info.with(module, contentRoot, info.getSourceRoot(), libraryClassRoot, sourceRootTypeData, orderEntries));
      }
    }
  }

  public void checkConsistency() {
    for (VirtualFile file : myProjectExcludedRoots) {
      assert file.exists() : file.getPath() + " does not exist";
    }
    for (VirtualFile file : myLibraryExcludedRoots) {
      assert file.exists() : file.getPath() + " does not exist";
    }

    for (VirtualFile file : myRoots.keySet()) {
      assert file.exists() : file.getPath() + " does not exist";
    }

    for (HashSet<VirtualFile> virtualFiles : myPackagePrefixRoots.values()) {
      for (VirtualFile file : virtualFiles) {
        assert file.exists() : file.getPath() + " does not exist";
      }
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


  private void fillMapWithOrderEntries(final VirtualFile root,
                                       @NotNull final Collection<OrderEntry> orderEntries,
                                       @Nullable final Module module,
                                       @Nullable final VirtualFile libraryClassRoot,
                                       @Nullable final VirtualFile librarySourceRoot) {

    assert root.exists();

    if (FileTypeRegistry.getInstance().isFileIgnored(root)) {
      return;
    }

    DirectoryInfo info = myRoots.get(root);
    if (info == null) return;

    if (module != null) {
      if (info.getModule() != module) return;
      if (!info.isInModuleSource()) return;
    } else if (libraryClassRoot != null) {
      if (info.getLibraryClassRoot() != libraryClassRoot) return;
      if (info.isInModuleSource()) return;
    } else if (librarySourceRoot != null) {
      if (!info.isInLibrarySource()) return;
      if (info.getSourceRoot() != librarySourceRoot) return;
      if (info.getLibraryClassRoot() != null) return;
    }

    OrderEntry[] orderEntriesArray = orderEntries.toArray(new OrderEntry[orderEntries.size()]);
    Arrays.sort(orderEntriesArray, DirectoryInfo.BY_OWNER_MODULE);

    myRoots.put(root, info.withInternedEntries(info.calcNewOrderEntries(orderEntriesArray, null, info.getOrderEntries())));
  }

  @NotNull
  private DirectoryInfo getOrCreateDirectoryInfo(VirtualFile root) {
    final DirectoryInfo existingInfo = myRoots.get(root);
    if (existingInfo != null) {
      return existingInfo;
    }

    return DirectoryInfo.createNew();
  }

  private void initializePrefix(final VirtualFile root, final String packagePrefix) {
    if (!myPackagePrefixRoots.containsKey(packagePrefix)) {
      myPackagePrefixRoots.put(packagePrefix, new HashSet<VirtualFile>());
    }
    myPackagePrefixRoots.get(packagePrefix).add(root);
  }

  @Nullable 
  public DirectoryInfo getInfoForDirectory(@NotNull final VirtualFile dir) {
    if (!dir.isValid()) {
      return null;
    }
    if (!dir.isDirectory()) {
      return myRoots.get(dir);
    }

    int count = 0;
    for (VirtualFile root = dir; root != null; root = root.getParent()) {
      if (++count > 1000) {
        throw new IllegalStateException("Possible loop in tree, started at " + dir.getName());
      }
      DirectoryInfo info = myInfoCache.get(root);
      if (info != null) {
        if (dir != root) {
          myInfoCache.put(dir, info);
        }
        return info == NULL_INFO ? null : info;
      }
      
      info = myRoots.get(root);
      if (info != null) {
        myInfoCache.put(dir, info);
        return info;
      }
      
      if (isAnyExcludeRoot(root) || FileTypeManager.getInstance().isFileIgnored(root)) {
        myInfoCache.put(dir, NULL_INFO);
        return null;
      }
    }

    myInfoCache.put(dir, NULL_INFO);
    return null;
  }

  private boolean isAnyExcludeRoot(VirtualFile root) {
    return myProjectExcludedRoots.contains(root) || myLibraryExcludedRoots.contains(root);
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
    Set<VirtualFile> packagePrefixRoots = myPackagePrefixRoots.get(packageName);
    if (packagePrefixRoots != null) {
      for (VirtualFile file : packagePrefixRoots) {
        if (isValidPackageDirectory(includeLibrarySources, file)) {
          result.add(file);
        }
      }
    }

    if (StringUtil.isNotEmpty(packageName) && !packageName.startsWith(".")) {
      String parentPackage = StringUtil.getPackageName(packageName);
      String shortName = StringUtil.getShortName(packageName);
      for (VirtualFile parentDir : getDirectoriesByPackageName(parentPackage, includeLibrarySources)) {
        VirtualFile child = parentDir.findChild(shortName);
        if (isValidPackageDirectory(includeLibrarySources, child) && child.isDirectory()) {
          result.add(child);
        }
      }
    }

    if (!result.isEmpty()) {
      cacheMap.put(packageName, result);
    }
    return result;
  }

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
      for (final Map.Entry<String, HashSet<VirtualFile>> entry : myPackagePrefixRoots.entrySet()) {
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

}