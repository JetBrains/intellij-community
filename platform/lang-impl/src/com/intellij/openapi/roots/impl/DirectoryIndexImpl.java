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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  private final Project myProject;

  private boolean myInitialized = false;
  private boolean myDisposed = false;
  private volatile IndexState myState;

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
    myState = new IndexState();
  }

  @NotNull
  public String getComponentName() {
    return "DirectoryIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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

    IndexState oldState = myState;
    myState = myState.copy();

    myState.doInitialize(reverseAllSets);

    Set<VirtualFile> keySet = myState.myDirToInfoMap.keySet();
    assert keySet.size() == oldState.myDirToInfoMap.keySet().size();
    for (VirtualFile file : keySet) {
      DirectoryInfo info1 = myState.myDirToInfoMap.get(file);
      DirectoryInfo info2 = oldState.myDirToInfoMap.get(file);
      assert info1.equals(info2);
    }

    assert myState.myPackageNameToDirsMap.keySet().size() == oldState.myPackageNameToDirsMap.keySet().size();
    for (Map.Entry<String, List<VirtualFile>> entry : myState.myPackageNameToDirsMap.entrySet()) {
      String packageName = entry.getKey();
      List<VirtualFile> dirs = entry.getValue();
      List<VirtualFile> dirs1 = oldState.myPackageNameToDirsMap.get(packageName);

      HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
      set1.addAll(dirs);
      HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
      set2.addAll(dirs1);
      assert set1.equals(set2);
    }
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public  void initialize() {
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
    long l = System.currentTimeMillis();
    doInitialize();
    LOG.info("Directory index initialized in " + (System.currentTimeMillis() - l) + " ms, indexed " + myState.myDirToInfoMap.size() + " directories");
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
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
    IndexState newState = new IndexState();
    newState.doInitialize(false);
    myState = newState;
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

  private static boolean isIgnored(@NotNull VirtualFile f) {
    return FileTypeManager.getInstance().isFileIgnored(f);
  }

  public DirectoryInfo getInfoForDirectory(VirtualFile dir) {
    checkAvailability();
    dispatchPendingEvents();

    return myState.myDirToInfoMap.get(dir);
  }

  @Override
  public boolean isProjectExcludeRoot(VirtualFile dir) {
    checkAvailability();
    return myState.myProjectExcludeRoots.contains(dir);
  }

  private final PackageSink mySink = new PackageSink();

  private static final Condition<VirtualFile> IS_VALID = new Condition<VirtualFile>() {
    public boolean value(final VirtualFile virtualFile) {
      return virtualFile.isValid();
    }
  };

  private class PackageSink extends QueryFactory<VirtualFile, Pair<IndexState, List<VirtualFile>>> {
    private PackageSink() {
      registerExecutor(new QueryExecutor<VirtualFile, Pair<IndexState, List<VirtualFile>>>() {
        public boolean execute(@NotNull final Pair<IndexState, List<VirtualFile>> stateAndDirs,
                               @NotNull final Processor<VirtualFile> consumer) {
          for (VirtualFile dir : stateAndDirs.second) {
            DirectoryInfo info = stateAndDirs.first.myDirToInfoMap.get(dir);
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
      checkAvailability();
      dispatchPendingEvents();

      IndexState state = myState;
      List<VirtualFile> allDirs = state.myPackageNameToDirsMap.get(packageName);
      if (allDirs == null) allDirs = Collections.emptyList();

      Query<VirtualFile> query = includeLibrarySources ? new CollectionQuery<VirtualFile>(allDirs)
                                                       : createQuery(Pair.create(state, allDirs));
      return new FilteredQuery<VirtualFile>(query, IS_VALID);
    }
  }

  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return mySink.search(packageName, includeLibrarySources);
  }

  @Override
  public String getPackageName(VirtualFile dir) {
    checkAvailability();
    return myState.myDirToPackageName.get(dir);
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

  @Nullable
  private static String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.isEmpty() ? subdirName : parentPackageName + "." + subdirName;
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    private final Key<List<VirtualFile>> FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    public void fileCreated(VirtualFileEvent event) {
      VirtualFile file = event.getFile();

      if (!file.isDirectory()) return;

      VirtualFile parent = file.getParent();
      if (parent == null) return;

      myState = updateStateWithNewFile(file, parent);
    }

    private IndexState updateStateWithNewFile(VirtualFile file, VirtualFile parent) {
      final IndexState originalState = myState;
      IndexState state = originalState;
      DirectoryInfo parentInfo = originalState.myDirToInfoMap.get(parent);

      // fill info for all nested roots
      for (Module eachModule : ModuleManager.getInstance(myProject).getModules()) {
        for (ContentEntry eachRoot : getContentEntries(eachModule)) {
          if (parentInfo != null && eachRoot == parentInfo.contentRoot) continue;

          if (FileUtil.startsWith(eachRoot.getUrl(), file.getUrl())) {
            String rel = FileUtil.getRelativePath(file.getUrl(), eachRoot.getUrl(), '/');
            if (rel != null) {
              VirtualFile f = file.findFileByRelativePath(rel);
              if (f != null) {
                if (state == originalState) state = state.copy();
                state.fillMapWithModuleContent(f, eachModule, f);
              }
            }
          }
        }
      }

      if (parentInfo == null) return state;

      Module module = parentInfo.module;

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        if (policy.isExcludeRoot(file)) return state;
      }

      if (state == originalState) state = state.copy();
      state.fillMapWithModuleContent(file, module, parentInfo.contentRoot);

      String parentPackage = state.myDirToPackageName.get(parent);

      if (module != null) {
        if (parentInfo.isInModuleSource) {
          String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
          state.fillMapWithModuleSource(file, module, newDirPackageName, parentInfo.sourceRoot, parentInfo.isTestSource);
        }
      }

      if (parentInfo.libraryClassRoot != null) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibraryClasses(file, newDirPackageName, parentInfo.libraryClassRoot);
      }

      if (parentInfo.isInLibrarySource) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibrarySources(file, newDirPackageName, parentInfo.sourceRoot);
      }

      if (!parentInfo.getOrderEntries().isEmpty()) {
        state.fillMapWithOrderEntries(file, parentInfo.getOrderEntries(), null, null, null, parentInfo);
      }
      return state;
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      if (!myState.myDirToInfoMap.containsKey(file)) return;

      final IndexState state = myState.copy();

      ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
      state.addDirsRecursively(list, file);
      file.putUserData(FILES_TO_RELEASE_KEY, list);
      myState = state;
    }

    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      List<VirtualFile> list = file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      IndexState copy = null;
      for (VirtualFile dir : list) {
        if (myState.myDirToInfoMap.containsKey(dir)) {
          if (copy == null) copy = myState.copy();

          copy.myDirToInfoMap.remove(dir);
          copy.setPackageName(dir, null);
        }
      }

      if (copy != null) {
        myState = copy;
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

  private class IndexState {
    final THashMap<VirtualFile, Set<String>> myExcludeRootsMap = new THashMap<VirtualFile, Set<String>>();
    final Set<VirtualFile> myProjectExcludeRoots = new THashSet<VirtualFile>();
    final Map<VirtualFile, DirectoryInfo> myDirToInfoMap = new THashMap<VirtualFile, DirectoryInfo>();
    final THashMap<String, List<VirtualFile>> myPackageNameToDirsMap = new THashMap<String, List<VirtualFile>>();
    final Map<VirtualFile, String> myDirToPackageName = new THashMap<VirtualFile, String>();

    public IndexState() {
    }

    private DirectoryInfo getOrCreateDirInfo(VirtualFile dir) {
      DirectoryInfo info = myDirToInfoMap.get(dir);
      if (info == null) {
        info = new DirectoryInfo();
        myDirToInfoMap.put(dir, info);
      }
      return info;
    }

    private void fillMapWithModuleContent(VirtualFile root, final Module module, final VirtualFile contentRoot) {

      VfsUtilCore.visitChildrenRecursively(root, new DirectoryVisitor() {
        
        @Override
        protected DirectoryInfo updateInfo(VirtualFile file) {
          if (isExcluded(contentRoot, file)) return null;
          if (isIgnored(file)) return null;

          DirectoryInfo info = getOrCreateDirInfo(file);

          if (info.module != null) { // module contents overlap
            DirectoryInfo parentInfo = myDirToInfoMap.get(file.getParent());
            if (parentInfo == null || !info.module.equals(parentInfo.module)) return null;
          }

          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          info.module = module;
          info.contentRoot = contentRoot;
        }
      });
    }

    private abstract class DirectoryVisitor extends VirtualFileVisitor {

      private final Stack<DirectoryInfo> myDirectoryInfoStack = new Stack<DirectoryInfo>();
      
      @Override
      public boolean visitFile(VirtualFile file) {
        if (!file.isDirectory()) return false;
        DirectoryInfo info = updateInfo(file);
        if (info != null) {
          myDirectoryInfoStack.push(info);
          return true;
        }
        return false; 
      }

      @Override
      public void afterChildrenVisited(VirtualFile file) {
        afterChildrenVisited(myDirectoryInfoStack.pop());
      }

      @Nullable
      protected abstract DirectoryInfo updateInfo(VirtualFile file);

      protected void afterChildrenVisited(DirectoryInfo info) {}
    }
    
    private boolean isExcluded(VirtualFile root, VirtualFile dir) {
      Set<String> excludes = myExcludeRootsMap.get(root);
      return excludes != null && excludes.contains(dir.getUrl());
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

    private void fillMapWithModuleSource(final VirtualFile dir, final Module module, final String packageName, final VirtualFile sourceRoot, final boolean isTestSource) {
      
      VfsUtilCore.visitChildrenRecursively(dir, new DirectoryVisitor() {
        
        private final Stack<String> myPackages = new Stack<String>();

        @Override
        protected DirectoryInfo updateInfo(VirtualFile file) {
          DirectoryInfo info = myDirToInfoMap.get(file);
          if (info == null) return null;
          if (!module.equals(info.module)) return null;

          if (info.isInModuleSource) { // module sources overlap
            String definedPackage = myDirToPackageName.get(file);
            if (definedPackage != null && definedPackage.isEmpty()) return null; // another source root starts here
          }

          info.isInModuleSource = true;
          info.isTestSource = isTestSource;
          info.sourceRoot = sourceRoot;

          String currentPackage;
          if (myPackages.isEmpty()) {
            currentPackage = packageName;
          }
          else {
            currentPackage = getPackageNameForSubdir(myPackages.peek(), file.getName());
          }
          myPackages.push(currentPackage);
          setPackageName(file, currentPackage);
          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          super.afterChildrenVisited(info);
          myPackages.pop();
        }
      });
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
        if (definedPackage != null && definedPackage.isEmpty()) return; // another library source root starts here
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
        if (definedPackage != null && definedPackage.isEmpty()) return; // another library root starts here
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

    private void initOrderEntries(Module module,
                                  MultiMap<VirtualFile, OrderEntry> depEntries,
                                  MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                  MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof ModuleOrderEntry) {
          final Module depModule = ((ModuleOrderEntry)orderEntry).getModule();
          if (depModule != null) {
            VirtualFile[] importedClassRoots =
              OrderEnumerator.orderEntries(depModule).exportedOnly().recursively().classes().usingCache().getRoots();
            for (VirtualFile importedClassRoot : importedClassRoots) {
              depEntries.putValue(importedClassRoot, orderEntry);
            }
          }
          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            depEntries.putValue(sourceRoot, orderEntry);
          }
        }
        else if (orderEntry instanceof ModuleSourceOrderEntry) {
          List<OrderEntry> oneEntryList = Arrays.asList(orderEntry);
          Module entryModule = orderEntry.getOwnerModule();

          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            fillMapWithOrderEntries(sourceRoot, oneEntryList, entryModule, null, null, null);
          }
        }
        else if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
          for (VirtualFile classRoot : classRoots) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
        }
      }
    }

    private void fillMapWithOrderEntries(MultiMap<VirtualFile, OrderEntry> depEntries,
                                         MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                         MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {
      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : depEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, null, null, null);
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, vRoot, null, null);
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, null, vRoot, null);
      }
    }

    private void setPackageName(VirtualFile dir, String newPackageName) {
      assert dir != null;

      String oldPackageName = myDirToPackageName.get(dir);
      if (oldPackageName != null) {
        List<VirtualFile> oldPackageDirs = myPackageNameToDirsMap.get(oldPackageName);
        final boolean removed = oldPackageDirs.remove(dir);
        assert removed;

        if (oldPackageDirs.isEmpty()) {
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

    private void fillMapWithOrderEntries(final VirtualFile root,
                                         final Collection<OrderEntry> orderEntries,
                                         final Module module,
                                         final VirtualFile libraryClassRoot,
                                         final VirtualFile librarySourceRoot,
                                         final DirectoryInfo parentInfo) {
      
      VfsUtilCore.visitChildrenRecursively(root, new DirectoryVisitor() {

        private final Stack<List<OrderEntry>> myEntries = new Stack<List<OrderEntry>>();

        @Override
        protected DirectoryInfo updateInfo(VirtualFile dir) {
          if (isIgnored(dir)) return null;

          DirectoryInfo info = myDirToInfoMap.get(dir); // do not create it here!
          if (info == null) return null;

          if (module != null) {
            if (info.module != module) return null;
            if (!info.isInModuleSource) return null;
          }
          else if (libraryClassRoot != null) {
            if (info.libraryClassRoot != libraryClassRoot) return null;
            if (info.isInModuleSource) return null;
          }
          else if (librarySourceRoot != null) {
            if (!info.isInLibrarySource) return null;
            if (info.sourceRoot != librarySourceRoot) return null;
            if (info.libraryClassRoot != null) return null;
          }

          List<OrderEntry> oldParentEntries = myEntries.isEmpty() ? null : myEntries.peek();
          final List<OrderEntry> oldEntries = info.getOrderEntries();
          myEntries.push(oldEntries);
          info.addOrderEntries(orderEntries, parentInfo, oldParentEntries);
          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          myEntries.pop();
        }
      });
    }

    private void doInitialize(boolean reverseAllSets/* for testing order independence*/) {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress == null) progress = new EmptyProgressIndicator();

      progress.pushState();

      progress.checkCanceled();
      progress.setText(ProjectBundle.message("project.index.scanning.files.progress"));

      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      if (reverseAllSets) modules = ArrayUtil.reverseArray(modules);

      initExcludedDirMap(modules, progress);

      for (Module module : modules) {
        initModuleContents(module, reverseAllSets, progress);
      }
      // Important! Because module's contents may overlap,
      // first modules should be marked and only after that sources markup
      // should be added. (src markup depends on module markup)
      for (Module module : modules) {
        initModuleSources(module, reverseAllSets, progress);
        initLibrarySources(module, progress);
        initLibraryClasses(module, progress);
      }

      progress.checkCanceled();
      progress.setText2("");

      MultiMap<VirtualFile, OrderEntry> depEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      for (Module module : modules) {
        initOrderEntries(module,
                         depEntries,
                         libClassRootEntries,
                         libSourceRootEntries);
      }
      fillMapWithOrderEntries(depEntries, libClassRootEntries, libSourceRootEntries);
    }

    private void initExcludedDirMap(Module[] modules, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.building.exclude.roots.progress"));

      // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
      // exclude root should exclude from its content root and all outer content roots

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
                  putForFileAndAllAncestors(myExcludeRootsMap, excludeRoot.getFile(), excludeRoot.getUrl());
                }
              }
            }

            putForFileAndAllAncestors(myExcludeRootsMap, contentRoot, excludeRoot.getUrl());
          }
        }
      }

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        for (VirtualFile file : policy.getExcludeRootsForProject()) {
          putForFileAndAllAncestors(myExcludeRootsMap, file, file.getUrl());
          myProjectExcludeRoots.add(file);
        }
      }
    }

    private void putForFileAndAllAncestors(Map<VirtualFile, Set<String>> map, VirtualFile file, String value) {
      while (true) {
        Set<String> set = map.get(file);
        if (set == null) {
          set = new THashSet<String>();
          map.put(file, set);
        }
        set.add(value);

        file = file.getParent();
        if (file == null) break;
      }
    }

    private void addDirsRecursively(ArrayList<VirtualFile> list, VirtualFile dir) {
      if (!myDirToInfoMap.containsKey(dir) || !(dir instanceof NewVirtualFile)) return;

      list.add(dir);

      for (VirtualFile child : ((NewVirtualFile)dir).getCachedChildren()) {
        if (child.isDirectory()) {
          addDirsRecursively(list, child);
        }
      }
    }

    public IndexState copy() {
      final IndexState copy = new IndexState();

      myExcludeRootsMap.forEachEntry(new TObjectObjectProcedure<VirtualFile, Set<String>>() {
        @Override
        public boolean execute(VirtualFile key, Set<String> value) {
          copy.myExcludeRootsMap.put(key, new THashSet<String>(value));
          return true;
        }
      });

      copy.myProjectExcludeRoots.addAll(myProjectExcludeRoots);
      copy.myDirToInfoMap.putAll(myDirToInfoMap);

      myPackageNameToDirsMap.forEachEntry(new TObjectObjectProcedure<String, List<VirtualFile>>() {
        @Override
        public boolean execute(String key, List<VirtualFile> value) {
          copy.myPackageNameToDirsMap.put(key, new SmartList<VirtualFile>(value));
          return true;
        }
      });

      copy.myDirToPackageName.putAll(myDirToPackageName);

      return copy;
    }
  }
}
