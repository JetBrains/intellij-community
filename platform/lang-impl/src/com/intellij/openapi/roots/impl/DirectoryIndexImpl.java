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

package com.intellij.openapi.roots.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  private final Project myProject;

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  private Map<VirtualFile, Set<String>> myExcludeRootsMap;
  private Set<VirtualFile> myProjectExcludeRoots;
  private Map<VirtualFile, DirectoryInfo> myDirToInfoMap = new THashMap<VirtualFile, DirectoryInfo>();
  private Map<String, List<VirtualFile>> myPackageNameToDirsMap = new THashMap<String, List<VirtualFile>>();
  private final Map<VirtualFile, String> myDirToPackageName = new THashMap<VirtualFile, String>();

  private final DirectoryIndexExcludePolicy[] myExcludePolicies;
  private final MessageBusConnection myConnection;

  public DirectoryIndexImpl(Project project, StartupManager startupManager) {
    myProject = project;
    myConnection = project.getMessageBus().connect(project);

    startupManager.registerPreStartupActivity(new Runnable() {
      public void run() {
        initialize();
      }
    });
    myExcludePolicies = Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myProject);
  }

  @NotNull
  public String getComponentName() {
    return "DirectoryIndex";
  }

  public void initComponent() {
  }

  public synchronized void disposeComponent() {
    myDisposed = true;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @TestOnly
  public void checkConsistency() {
    doCheckConsistency(false);
    doCheckConsistency(true);
  }

  @TestOnly
  private void doCheckConsistency(boolean reverseAllSets) {
    assert myInitialized;
    assert !myDisposed;

    Map<VirtualFile, DirectoryInfo> oldDirToInfoMap = myDirToInfoMap;
    myDirToInfoMap = new THashMap<VirtualFile, DirectoryInfo>();

    Map<String, List<VirtualFile>> oldPackageNameToDirsMap = myPackageNameToDirsMap;
    myPackageNameToDirsMap = new THashMap<String, List<VirtualFile>>();

    doInitialize(reverseAllSets);

    Set<VirtualFile> keySet = myDirToInfoMap.keySet();
    assert keySet.size() == oldDirToInfoMap.keySet().size();
    for (VirtualFile file : keySet) {
      DirectoryInfo info1 = myDirToInfoMap.get(file);
      DirectoryInfo info2 = oldDirToInfoMap.get(file);
      assert info1.equals(info2);
    }

    assert myPackageNameToDirsMap.keySet().size() == oldPackageNameToDirsMap.keySet().size();
    for (Map.Entry<String, List<VirtualFile>> entry : myPackageNameToDirsMap.entrySet()) {
      String packageName = entry.getKey();
      List<VirtualFile> dirs = entry.getValue();
      List<VirtualFile> dirs1 = oldPackageNameToDirsMap.get(packageName);

      HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
      set1.addAll(dirs);
      HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
      set2.addAll(dirs1);
      assert set1.equals(set2);
    }
  }

  public synchronized boolean isInitialized() {
    return myInitialized;
  }

  public synchronized void initialize() {
    if (myInitialized) {
      LOG.error("Directory index is already initialized.");
      return;
    }

    if (myDisposed) {
      LOG.error("Directory index is already disposed for this project");
      return;
    }

    subscribeToFileChanges();

    myInitialized = true;
    doInitialize();
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new MyVirtualFileListener()));
  }

  private void doInitialize() {
    doInitialize(false);
  }

  private synchronized void doInitialize(boolean reverseAllSets/* for testing order independence*/) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress == null) progress = new EmptyProgressIndicator();

    progress.pushState();

    progress.checkCanceled();
    progress.setText(ProjectBundle.message("project.index.scanning.files.progress"));

    cleanAllMaps();

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (reverseAllSets) modules = ArrayUtil.reverseArray(modules);

    initExcludedDirMap(modules, progress);

    for (Module module : modules) {
      initModuleContents(module, reverseAllSets, progress);
      initModuleSources(module, reverseAllSets, progress);
      initLibrarySources(module, progress);
      initLibraryClasses(module, progress);
    }

    progress.checkCanceled();
    progress.setText2("");

    for (Module module : modules) {
      initOrderEntries(module);
    }

    progress.popState();
  }

  private void cleanAllMaps() {
    myDirToInfoMap.clear();
    myPackageNameToDirsMap.clear();
    myDirToPackageName.clear();
  }

  private void initExcludedDirMap(Module[] modules, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.building.exclude.roots.progress"));

    // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
    // exclude root should exclude from its content root and all outer content roots
    Map<VirtualFile, Set<String>> result = new THashMap<VirtualFile, Set<String>>();
    Set<VirtualFile> projectExcludeRoots = new THashSet<VirtualFile>();

    for (Module module : modules) {
      for (ContentEntry contentEntry : getContentEntries(module)) {
        VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) continue;

        ExcludeFolder[] excludeRoots = contentEntry.getExcludeFolders();
        for (ExcludeFolder excludeRoot : excludeRoots) {
          // Output paths should be excluded (if marked as such) regardless if they're under corresponding module's content root
          if (excludeRoot.getFile() != null) {
            if (!FileUtil.startsWith(contentRoot.getUrl(), excludeRoot.getUrl())) {
              if (isExcludeRootForModule(module, excludeRoot.getFile())) {
                putForFileAndAllAncestors(result, excludeRoot.getFile(), excludeRoot.getUrl());
              }
            }
          }

          putForFileAndAllAncestors(result, contentRoot, excludeRoot.getUrl());
        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
      for (VirtualFile file : policy.getExcludeRootsForProject()) {
        putForFileAndAllAncestors(result, file, file.getUrl());
        projectExcludeRoots.add(file);
      }
    }

    myExcludeRootsMap = result;
    myProjectExcludeRoots = projectExcludeRoots;
  }

  private static void putForFileAndAllAncestors(Map<VirtualFile, Set<String>> map, VirtualFile file, String value) {
    while (true) {
      Set<String> set = map.get(file);
      if (set == null) {
        set = new HashSet<String>();
        map.put(file, set);
      }
      set.add(value);

      file = file.getParent();
      if (file == null) break;
    }
  }

  private boolean isExcludeRootForModule(Module module, VirtualFile excludeRoot) {
    for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
      if (policy.isExcludeRootForModule(module, excludeRoot)) return true;
    }
    return false;
  }

  private static ContentEntry[] getContentEntries(Module module) {
    return ModuleRootManager.getInstance(module).getContentEntries();
  }

  private static OrderEntry[] getOrderEntries(Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  private void initModuleContents(Module module, boolean reverseAllSets, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.module.content.progress", module.getName()));

    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] contentRoots = rootManager.getContentRoots();
    if (reverseAllSets) {
      contentRoots = ArrayUtil.reverseArray(contentRoots);
    }

    for (final VirtualFile contentRoot : contentRoots) {
      fillMapWithModuleContent(contentRoot, module, contentRoot);
    }
  }

  private void fillMapWithModuleContent(VirtualFile dir, Module module, VirtualFile contentRoot) {
    if (isExcluded(contentRoot, dir)) return;
    if (isIgnored(dir)) return;

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.module != null) { // module contents overlap
      DirectoryInfo parentInfo = myDirToInfoMap.get(dir.getParent());
      if (parentInfo == null || !info.module.equals(parentInfo.module)) return; // content of another module is below this module's content
    }

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        fillMapWithModuleContent(child, module, contentRoot);
      }
    }

    // important to change module AFTER processing children - to handle overlapping modules
    info.module = module;
    info.contentRoot = contentRoot;
  }

  private boolean isExcluded(VirtualFile root, VirtualFile dir) {
    Set<String> excludes = myExcludeRootsMap.get(root);
    return excludes != null && excludes.contains(dir.getUrl());
  }

  private void initModuleSources(Module module, boolean reverseAllSets, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.module.sources.progress", module.getName()));

    ContentEntry[] contentEntries = getContentEntries(module);

    if (reverseAllSets) {
      contentEntries = ArrayUtil.reverseArray(contentEntries);
    }

    for (ContentEntry contentEntry : contentEntries) {
      SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      if (reverseAllSets) {
        sourceFolders = ArrayUtil.reverseArray(sourceFolders);
      }
      for (SourceFolder sourceFolder : sourceFolders) {
        VirtualFile dir = sourceFolder.getFile();
        if (dir != null) {
          fillMapWithModuleSource(dir, module, sourceFolder.getPackagePrefix(), dir, sourceFolder.isTestSource());
        }
      }
    }
  }

  private void fillMapWithModuleSource(VirtualFile dir, Module module, String packageName, VirtualFile sourceRoot, boolean isTestSource) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) return;
    if (!module.equals(info.module)) return;

    if (info.isInModuleSource) { // module sources overlap
      String definedPackage = myDirToPackageName.get(dir);
      if (definedPackage != null && definedPackage.length() == 0) return; // another source root starts here
    }

    info.isInModuleSource = true;
    info.isTestSource = isTestSource;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, packageName);

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithModuleSource(child, module, childPackageName, sourceRoot, isTestSource);
      }
    }
  }

  private void initLibrarySources(Module module, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.library.sources.progress", module.getName()));

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
      if (isLibrary) {
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (final VirtualFile sourceRoot : sourceRoots) {
          fillMapWithLibrarySources(sourceRoot, "", sourceRoot);
        }
      }
    }
  }

  private void fillMapWithLibrarySources(VirtualFile dir, String packageName, VirtualFile sourceRoot) {
    if (isIgnored(dir)) return;

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.isInLibrarySource) { // library sources overlap
      String definedPackage = myDirToPackageName.get(dir);
      if (definedPackage != null && definedPackage.length() == 0) return; // another library source root starts here
    }

    info.isInModuleSource = false;
    info.isInLibrarySource = true;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, packageName);

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibrarySources(child, childPackageName, sourceRoot);
      }
    }
  }

  private void initLibraryClasses(Module module, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.library.classes.progress", module.getName()));

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
      if (isLibrary) {
        VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
        for (final VirtualFile classRoot : classRoots) {
          fillMapWithLibraryClasses(classRoot, "", classRoot);
        }
      }
    }
  }

  private void fillMapWithLibraryClasses(VirtualFile dir, String packageName, VirtualFile classRoot) {
    if (isIgnored(dir)) return;

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.libraryClassRoot != null) { // library classes overlap
      String definedPackage = myDirToPackageName.get(dir);
      if (definedPackage != null && definedPackage.length() == 0) return; // another library root starts here
    }

    info.libraryClassRoot = classRoot;

    if (!info.isInModuleSource && !info.isInLibrarySource) {
      setPackageName(dir, packageName);
    }

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibraryClasses(child, childPackageName, classRoot);
      }
    }
  }

  private void initOrderEntries(Module module) {
    Map<VirtualFile, List<OrderEntry>> depEntries = new HashMap<VirtualFile, List<OrderEntry>>();
    Map<VirtualFile, List<OrderEntry>> libClassRootEntries = new HashMap<VirtualFile, List<OrderEntry>>();
    Map<VirtualFile, List<OrderEntry>> libSourceRootEntries = new HashMap<VirtualFile, List<OrderEntry>>();

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module depModule = ((ModuleOrderEntry)orderEntry).getModule();
        if (depModule != null) {
          VirtualFile[] importedClassRoots = OrderEnumerator.orderEntries(depModule).exportedOnly().recursively().classes().usingCache().getRoots();
          for (VirtualFile importedClassRoot : importedClassRoots) {
            addEntryToMap(importedClassRoot, orderEntry, depEntries);
          }
        }
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          addEntryToMap(sourceRoot, orderEntry, depEntries);
        }
      }
      else if (orderEntry instanceof ModuleSourceOrderEntry) {
        List<OrderEntry> oneEntryList = Arrays.asList(orderEntry);
        Module entryModule = orderEntry.getOwnerModule();

        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          fillMapWithOrderEntries(sourceRoot, oneEntryList, entryModule, null, null, null, null);
        }
      }
      else if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
        VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
        for (VirtualFile classRoot : classRoots) {
          addEntryToMap(classRoot, orderEntry, libClassRootEntries);
        }
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          addEntryToMap(sourceRoot, orderEntry, libSourceRootEntries);
        }
      }
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : depEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, null, null, null, null);
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, vRoot, null, null, null);
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, null, vRoot, null, null);
    }
  }

  private static void addEntryToMap(final VirtualFile vRoot, final OrderEntry entry, final Map<VirtualFile, List<OrderEntry>> map) {
    List<OrderEntry> list = map.get(vRoot);
    if (list == null) {
      list = new ArrayList<OrderEntry>();
      map.put(vRoot, list);
    }
    list.add(entry);
  }

  private void fillMapWithOrderEntries(VirtualFile dir,
                                       List<OrderEntry> orderEntries,
                                       Module module,
                                       VirtualFile libraryClassRoot,
                                       VirtualFile librarySourceRoot,
                                       DirectoryInfo parentInfo,
                                       final List<OrderEntry> oldParentEntries) {
    if (isIgnored(dir)) return;

    DirectoryInfo info = myDirToInfoMap.get(dir); // do not create it here!
    if (info == null) return;

    if (module != null) {
      if (info.module != module) return;
      if (!info.isInModuleSource) return;
    }
    else if (libraryClassRoot != null) {
      if (info.libraryClassRoot != libraryClassRoot) return;
      if (info.isInModuleSource) return;
    }
    else if (librarySourceRoot != null) {
      if (!info.isInLibrarySource) return;
      if (info.sourceRoot != librarySourceRoot) return;
      if (info.libraryClassRoot != null) return;
    }

    final List<OrderEntry> oldEntries = info.getOrderEntries();
    info.addOrderEntries(orderEntries, parentInfo, oldParentEntries);

    final VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        fillMapWithOrderEntries(child, orderEntries, module, libraryClassRoot, librarySourceRoot, info, oldEntries);
      }
    }
  }

  private static boolean isIgnored(VirtualFile f) {
    return FileTypeManager.getInstance().isFileIgnored(f.getName());
  }

  public synchronized DirectoryInfo getInfoForDirectory(VirtualFile dir) {
    checkAvailability();
    dispatchPendingEvents();

    return myDirToInfoMap.get(dir);
  }

  @Override
  public synchronized boolean isProjectExcludeRoot(VirtualFile dir) {
    checkAvailability();
    return myProjectExcludeRoots.contains(dir);
  }

  private final PackageSink mySink = new PackageSink();

  private static final Condition<VirtualFile> IS_VALID = new Condition<VirtualFile>() {
    public boolean value(final VirtualFile virtualFile) {
      return virtualFile.isValid();
    }
  };

  private class PackageSink extends QueryFactory<VirtualFile, List<VirtualFile>> {
    private PackageSink() {
      registerExecutor(new QueryExecutor<VirtualFile, List<VirtualFile>>() {
        public boolean execute(final List<VirtualFile> allDirs, final Processor<VirtualFile> consumer) {
          for (VirtualFile dir : allDirs) {
            DirectoryInfo info = getInfoForDirectory(dir);
            assert info != null;

            if (!info.isInLibrarySource || info.libraryClassRoot != null) {
              if (!consumer.process(dir)) return false;
            }
          }
          return true;
        }
      });
    }

    public Query<VirtualFile> search(@NotNull String packageName, boolean includeLibrarySources) {
      List<VirtualFile> allDirs = doGetDirectoriesByPackageName(packageName);
      return new FilteredQuery<VirtualFile>(includeLibrarySources ? new CollectionQuery<VirtualFile>(allDirs) : createQuery(allDirs),
                                            IS_VALID);
    }
  }

  @NotNull
  public synchronized Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    checkAvailability();
    return mySink.search(packageName, includeLibrarySources);
  }

  @Override
  public synchronized String getPackageName(VirtualFile dir) {
    checkAvailability();
    return myDirToPackageName.get(dir);
  }

  @NotNull
  private synchronized List<VirtualFile> doGetDirectoriesByPackageName(@NotNull String packageName) {
    dispatchPendingEvents();

    List<VirtualFile> dirs = myPackageNameToDirsMap.get(packageName);
    return dirs != null ? dirs : Collections.<VirtualFile>emptyList();
  }

  private void dispatchPendingEvents() {
    myConnection.deliverImmediately();
  }

  private void checkAvailability() {
    if (!myInitialized) {
      LOG.error("Directory index is not initialized yet for " + myProject);
    }

    if (myDisposed) {
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }

  private DirectoryInfo getOrCreateDirInfo(VirtualFile dir) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) {
      info = new DirectoryInfo();
      myDirToInfoMap.put(dir, info);
    }
    return info;
  }

  private void setPackageName(VirtualFile dir, String newPackageName) {
    assert dir != null;

    String oldPackageName = myDirToPackageName.get(dir);
    if (oldPackageName != null) {
      List<VirtualFile> oldPackageDirs = myPackageNameToDirsMap.get(oldPackageName);
      final boolean removed = oldPackageDirs.remove(dir);
      assert removed;

      if (oldPackageDirs.size() == 0) {
        myPackageNameToDirsMap.remove(oldPackageName);
      }
    }

    if (newPackageName != null) {
      List<VirtualFile> newPackageDirs = myPackageNameToDirsMap.get(newPackageName);
      if (newPackageDirs == null) {
        newPackageDirs = new SmartList<VirtualFile>();
        myPackageNameToDirsMap.put(newPackageName, newPackageDirs);
      }
      newPackageDirs.add(dir);

      myDirToPackageName.put(dir, newPackageName);
    }
    else {
      myDirToPackageName.remove(dir);
    }
  }

  @Nullable
  private static String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.length() > 0 ? parentPackageName + "." + subdirName : subdirName;
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    private final Key<List<VirtualFile>> FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    public void fileCreated(VirtualFileEvent event) {
      VirtualFile file = event.getFile();

      if (!file.isDirectory()) return;

      VirtualFile parent = file.getParent();
      if (parent == null) return;

      synchronized (DirectoryIndexImpl.this) {
        DirectoryInfo parentInfo = myDirToInfoMap.get(parent);

        // fill info for all nested roots
        for (Module eachModule : ModuleManager.getInstance(myProject).getModules()) {
          for (ContentEntry eachRoot : getContentEntries(eachModule)) {
            if (parentInfo != null && eachRoot == parentInfo.contentRoot) continue;

            if (FileUtil.startsWith(eachRoot.getUrl(), file.getUrl())) {
              String rel = FileUtil.getRelativePath(file.getUrl(), eachRoot.getUrl(), '/');
              if (rel != null) {
                VirtualFile f = file.findFileByRelativePath(rel);
                fillMapWithModuleContent(f, eachModule, f);
              }
            }
          }
        }

        if (parentInfo == null) return;

        Module module = parentInfo.module;

        for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
          if (policy.isExcludeRoot(file)) return;
        }

        fillMapWithModuleContent(file, module, parentInfo.contentRoot);

        String parentPackage = myDirToPackageName.get(parent);

        if (module != null) {
          if (parentInfo.isInModuleSource) {
            String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
            fillMapWithModuleSource(file, module, newDirPackageName, parentInfo.sourceRoot, parentInfo.isTestSource);
          }
        }

        if (parentInfo.libraryClassRoot != null) {
          String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
          fillMapWithLibraryClasses(file, newDirPackageName, parentInfo.libraryClassRoot);
        }

        if (parentInfo.isInLibrarySource) {
          String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
          fillMapWithLibrarySources(file, newDirPackageName, parentInfo.sourceRoot);
        }

        if (!parentInfo.getOrderEntries().isEmpty()) {
          fillMapWithOrderEntries(file, parentInfo.getOrderEntries(), null, null, null, parentInfo, null);
        }
      }
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      synchronized (DirectoryIndexImpl.this) {
        VirtualFile file = event.getFile();
        if (!file.isDirectory()) return;
        if (!myDirToInfoMap.containsKey(file)) return;

        ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
        addDirsRecursively(list, file);
        file.putUserData(FILES_TO_RELEASE_KEY, list);
      }
    }

    private void addDirsRecursively(ArrayList<VirtualFile> list, VirtualFile dir) {
      synchronized (DirectoryIndexImpl.this) {
        if (!myDirToInfoMap.containsKey(dir) || !(dir instanceof NewVirtualFile)) return;

        list.add(dir);

        for (VirtualFile child : ((NewVirtualFile)dir).getCachedChildren()) {
          if (child.isDirectory()) {
            addDirsRecursively(list, child);
          }
        }
      }
    }

    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      List<VirtualFile> list = file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      synchronized (DirectoryIndexImpl.this) {
        for (VirtualFile dir : list) {
          DirectoryInfo info = myDirToInfoMap.remove(dir);
          if (info != null) {
            setPackageName(dir, null);
          }
        }
      }
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (file.isDirectory()) {
        doInitialize();
      }
    }

    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        VirtualFile file = event.getFile();
        if (file.isDirectory()) {
          doInitialize();
        }
      }
    }
  }
}
