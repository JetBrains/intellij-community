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

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");
  private static final boolean CHECK = ApplicationManager.getApplication().isUnitTestMode();
  private static final TObjectHashingStrategy<int[]> INT_ARRAY_STRATEGY = new TObjectHashingStrategy<int[]>() {
    @Override
    public int computeHashCode(int[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(int[] o1, int[] o2) {
      return Arrays.equals(o1, o2);
    }
  };

  private final ManagingFS myPersistence;
  private final Project myProject;
  private final MessageBusConnection myConnection;
  private final DirectoryIndexExcludePolicy[] myExcludePolicies;

  private volatile IndexState myState = new IndexState();
  private volatile boolean myInitialized = false;
  private volatile boolean myDisposed = false;
  private final PackageSink mySink = new PackageSink();
  private static final boolean ourUseRootIndexOnly = Registry.is("directory.index.use.root.index");
  private static final boolean ourCompareImplementations = Registry.is("directory.index.compare.implementations");
  private volatile RootIndex myRootIndex = null;

  public DirectoryIndexImpl(@NotNull ManagingFS managingFS, @NotNull Project project, @NotNull StartupManager startupManager) {
    myPersistence = managingFS;
    myProject = project;
    myConnection = project.getMessageBus().connect(project);
    myExcludePolicies = Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myProject);
    startupManager.registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        initialize();
      }
    });
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myDisposed = true;
        myState.multiDirPackages.clear();
        myState.myDirToInfoMap.clear();
        myState.myDirToPackageName.clear();
        myState.myExcludeRootsMap.clear();
        myState.myPackageNameToDirsMap.clear();
        myState.myProjectExcludeRoots.clear();
        myState.myRootTypeId.clear();
        myState.myRootTypes.clear();
        myRootIndex = null;
      }
    });
  }

  public void initialize() {
    subscribeToFileChanges();

    if (myInitialized) {
      LOG.error("Directory index is already initialized.");
      return;
    }

    if (myDisposed) {
      LOG.error("Directory index is already disposed for this project");
      return;
    }

    myInitialized = true;
    long l = System.currentTimeMillis();
    doInitialize();
    LOG.info("Directory index initialized in " +
             (System.currentTimeMillis() - l) +
             " ms, indexed " +
             myState.myDirToInfoMap.size() +
             " directories");

    markContentRootsForRefresh();
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener.Adapter() {
      @Override
      public void fileTypesChanged(FileTypeEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyVirtualFileListener());
  }

  private void markContentRootsForRefresh() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        if (contentRoot instanceof NewVirtualFile) {
          ((NewVirtualFile)contentRoot).markDirtyRecursively();
        }
      }
    }
  }

  private void dispatchPendingEvents() {
    myConnection.deliverImmediately();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter implements BulkFileListener {
    private static final int MAX_DEPTH_TO_COUNT = 20;
    private static final int DIRECTORIES_CHANGED_THRESHOLD = 50;

    @Override
    public void fileCreated(VirtualFileEvent event) {
      VirtualFile file = event.getFile();

      if (!file.isDirectory()) return;

      VirtualFile parent = file.getParent();
      if (!(parent instanceof NewVirtualFile)) return;
      DirectoryInfo existing = myState.getInfo(((NewVirtualFile)file).getId());
      assert existing == null : file + " -> " + existing;
      IndexState newState = updateStateWithNewFile((NewVirtualFile)file, (NewVirtualFile)parent);
      replaceState(newState);
    }

    @NotNull
    private IndexState updateStateWithNewFile(@NotNull NewVirtualFile file, @NotNull NewVirtualFile parent) {
      final IndexState originalState = myState;
      IndexState state = originalState;
      int parentId = parent.getId();
      DirectoryInfo parentInfo = originalState.getInfo(parentId);
      if (parentInfo != null) {
        assertAncestor(parentInfo, parent, parentId);
      }

      // fill info for all nested roots
      String fileUrl = file.getUrl();
      for (Module eachModule : ModuleManager.getInstance(myProject).getModules()) {
        for (ContentEntry contentRoot : getContentEntries(eachModule)) {
          if (parentInfo != null) {
            VirtualFile contFile = contentRoot.getFile();
            if (contFile != null && contFile.equals(parentInfo.getContentRoot())) continue;
          }

          String contentRootUrl = contentRoot.getUrl();
          if (FileUtil.startsWith(contentRootUrl, fileUrl)) {
            String rel = FileUtil.getRelativePath(fileUrl, contentRootUrl, '/');
            if (rel != null) {
              VirtualFile f = file.findFileByRelativePath(rel);
              if (f instanceof NewVirtualFile) {
                if (state == originalState) state = state.copy(null);
                state.fillMapWithModuleContent((NewVirtualFile)f, eachModule, (NewVirtualFile)f, null);
              }
            }
          }
        }
      }

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        if (policy.isExcludeRoot(file)) {
          if (parentInfo == null || parentInfo.getContentRoot() == null) {
            if (state == originalState) state = state.copy(null);
            state.myProjectExcludeRoots.add(file.getId());
          }
          return state;
        }
      }

      if (parentInfo == null) return state;
      Module module = parentInfo.getModule();

      if (state == originalState) state = state.copy(null);
      VirtualFile parentContentRoot = parentInfo.getContentRoot();
      state.fillMapWithModuleContent(file, module, (NewVirtualFile)parentContentRoot, null);

      String parentPackage = state.getPackageNameForDirectory(parent);
      TObjectIntHashMap<String> interned = new TObjectIntHashMap<String>();

      if (module != null) {
        if (parentInfo.isInModuleSource()) {
          String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
          state.fillMapWithModuleSource(module, (NewVirtualFile)parentContentRoot, file, newDirPackageName,
                                        (NewVirtualFile)parentInfo.getSourceRoot(), parentInfo.getSourceRootTypeId(), null, interned);
        }
      }

      if (parentInfo.hasLibraryClassRoot()) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibraryClasses(file, newDirPackageName, (NewVirtualFile)parentInfo.getLibraryClassRoot(), null, interned, null);
      }

      if (parentInfo.isInLibrarySource()) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibrarySources(file, newDirPackageName, (NewVirtualFile)parentInfo.getSourceRoot(), null, interned, null);
      }

      OrderEntry[] entries = parentInfo.getOrderEntries();
      if (entries.length != 0) {
        state.fillMapWithOrderEntries(file, entries, null, null, null, parentInfo, null);
      }
      return state;
    }

    private final Key<int[]> FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      if (myState.getInfo(((NewVirtualFile)file).getId()) == null) return;

      TIntArrayList list = new TIntArrayList();
      addDirsRecursively(myState, list, file);
      file.putUserData(FILES_TO_RELEASE_KEY, list.toNativeArray());
    }

    private void addDirsRecursively(@NotNull IndexState state, @NotNull TIntArrayList list, @NotNull VirtualFile dir) {
      if (!(dir instanceof NewVirtualFile)) return;
      int id = ((NewVirtualFile)dir).getId();
      if (state.getInfo(id) == null) return;

      list.add(id);

      for (VirtualFile child : ((NewVirtualFile)dir).getCachedChildren()) {
        if (child.isDirectory()) {
          addDirsRecursively(state, list, child);
        }
      }
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      final int[] list = file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      IndexState copy = null;
      for (int id : list) {
        if (myState.getInfo(id) != null) {
          if (copy == null) {
            copy = myState.copy(new TIntProcedure() {
              @Override
              public boolean execute(int fid) {
                return ArrayUtil.indexOf(list, fid) == -1;
              }
            });
          }

          copy.myDirToInfoMap.remove(id);
          copy.setPackageName(id, null);
        }
      }

      if (copy != null) {
        replaceState(copy);
      }
      myState.assertAncestorsConsistent();
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (file.isDirectory()) {
        doInitialize();
      }
      myState.assertAncestorsConsistent();
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        VirtualFile file = event.getFile();
        if (file.isDirectory()) {
          doInitialize();
        }
      }
      myState.assertAncestorsConsistent();
    }

    private boolean myBatchChangePlanned;

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
      if (ourUseRootIndexOnly) {
        return;
      }

      myBatchChangePlanned = false;
      int directoriesRemoved = 0;
      int directoriesCreated = 0;

      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          VirtualFile file = event.getFile();
          if (file != null && file.isDirectory()) {
            directoriesRemoved += 1 + countDirectories(file, MAX_DEPTH_TO_COUNT);
          }
        }
        else if (event instanceof VFileCreateEvent) {
          VirtualFile file = event.getFile();
          if (file != null && file.isDirectory() ||
              file == null && ((VFileCreateEvent)event).isDirectory()) {
            directoriesCreated += 1 + countDirectories(file, MAX_DEPTH_TO_COUNT);
          }
        }
      }

      final boolean willDoBatchUpdate = directoriesCreated + directoriesRemoved > DIRECTORIES_CHANGED_THRESHOLD;
      if (willDoBatchUpdate) {
        myBatchChangePlanned = true;
        LOG.info("Too many directories created / deleted: " + directoriesCreated + "," + directoriesRemoved + ", will rebuild index state");
      }
      else {
        for (VFileEvent event : events) {
          BulkVirtualFileListenerAdapter.fireBefore(this, event);
        }
      }
    }

    private int countDirectories(@Nullable VirtualFile file, int depth) {
      if (!(file instanceof NewVirtualFile)) return 0;

      int counter = 0;
      for (VirtualFile child : ((NewVirtualFile)file).iterInDbChildren()) {
        if (child.isDirectory()) counter += 1 + (depth > 0 ? countDirectories(child, depth - 1) : 0);
      }
      return counter;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      RootIndex rootIndex = myRootIndex;
      if (rootIndex != null && !rootIndex.handleAfterEvent(events)) {
        myRootIndex = null;
      }
      if (ourUseRootIndexOnly) {
        return;
      }

      if (myBatchChangePlanned) {
        myBatchChangePlanned = false;
        long started = System.currentTimeMillis();
        doInitialize();
        LOG.info("Rebuilt indexstate for " + (System.currentTimeMillis() - started));
      }
      else {
        for (VFileEvent event : events) {
          BulkVirtualFileListenerAdapter.fireAfter(this, event);
        }
      }
    }
  }

  private void replaceState(IndexState newState) {
    newState.writable = false;
    myState = newState;
  }

  private class PackageSink extends QueryFactory<VirtualFile, Pair<IndexState, List<VirtualFile>>> {
    private final Condition<VirtualFile> IS_VALID = new Condition<VirtualFile>() {
      @Override
      public boolean value(final VirtualFile virtualFile) {
        return virtualFile.isValid();
      }
    };

    private PackageSink() {
      registerExecutor(new QueryExecutor<VirtualFile, Pair<IndexState, List<VirtualFile>>>() {
        @Override
        public boolean execute(@NotNull final Pair<IndexState, List<VirtualFile>> stateAndDirs,
                               @NotNull final Processor<VirtualFile> consumer) {
          for (VirtualFile dir : stateAndDirs.second) {
            DirectoryInfo info = stateAndDirs.first.myDirToInfoMap.get(((NewVirtualFile)dir).getId());
            assert info != null;

            if (!info.isInLibrarySource() || info.isInModuleSource() || info.hasLibraryClassRoot()) {
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
      int[] allDirs = state.getDirsForPackage(internPackageName(packageName, null));
      if (allDirs == null) allDirs = ArrayUtil.EMPTY_INT_ARRAY;

      List<VirtualFile> files = new ArrayList<VirtualFile>(allDirs.length);
      for (int dir : allDirs) {
        VirtualFile file = findFileById(dir);
        if (file != null) {
          files.add(file);
        }
      }

      Query<VirtualFile> query = includeLibrarySources ? new CollectionQuery<VirtualFile>(files) : createQuery(Pair.create(state, files));
      return new FilteredQuery<VirtualFile>(query, IS_VALID);
    }
  }

  @Override
  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {

    RootIndex rootIndex = getRootIndex();
    if (rootIndex != null) {
      Collection<VirtualFile> riResult = rootIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
      if (ourUseRootIndexOnly) {
        return new CollectionQuery<VirtualFile>(riResult);
      }

      Query<VirtualFile> standardResult = mySink.search(packageName, includeLibrarySources);
      Collection<VirtualFile> standard = standardResult.findAll();
      if (!new HashSet<VirtualFile>(riResult).equals(new HashSet<VirtualFile>(standard))) {
        for (VirtualFile file : standard) {
          String path = file.getPath();
          if (path.substring(path.length() - packageName.length()).contains(".")) {
            return standardResult; // standard and rootIndex return different results for directories with dot in name
          }
        }
        assertConsistentResult(packageName, riResult, standard);
      }
    }

    return mySink.search(packageName, includeLibrarySources);
  }

  @Nullable
  private RootIndex getRootIndex() {
    if (!ourUseRootIndexOnly && !ourCompareImplementations) {
      return null;
    }
    RootIndex rootIndex = myRootIndex;
    if (rootIndex == null) {
      myRootIndex = rootIndex = new RootIndex(myProject);
    }
    return rootIndex;
  }

  @Override
  @TestOnly
  public void checkConsistency() {
    RootIndex rootIndex = getRootIndex();
    if (rootIndex != null) {
      rootIndex.checkConsistency();
    }
    if (ourUseRootIndexOnly) {
      return;
    }

    doCheckConsistency(false);
    doCheckConsistency(true);
  }

  @TestOnly
  public void assertAncestorConsistent() {
    myState.assertAncestorsConsistent();
  }

  @TestOnly
  private void doCheckConsistency(boolean reverseAllSets) {
    assert myInitialized;
    assert !myDisposed;
    myState.assertNotWritable();

    final IndexState oldState = myState;
    myState.assertAncestorsConsistent();
    replaceState(myState.copy(null));
    myState.writable = true;

    myState.doInitialize(reverseAllSets);
    myState.writable = false;

    int[] keySet = myState.myDirToInfoMap.keys();
    assert keySet.length == oldState.myDirToInfoMap.keys().length;
    for (int file : keySet) {
      DirectoryInfo info1 = myState.getInfo(file);
      DirectoryInfo info2 = oldState.getInfo(file);
      assert info1 != null;
      assert info1.equals(info2);
      info1.assertConsistency();
    }

    assert myState.myPackageNameToDirsMap.size() == oldState.myPackageNameToDirsMap.size();
    myState.myPackageNameToDirsMap.forEachEntry(new TObjectIntProcedure<int[]>() {
      @Override
      public boolean execute(int[] packageName, int i) {
        int[] dirs = oldState.getDirsForPackage(packageName);
        int[] dirs1 = myState.getDirsForPackage(packageName);

        TIntHashSet set1 = new TIntHashSet(dirs);
        TIntHashSet set2 = new TIntHashSet(dirs1);
        assert set1.equals(set2);
        return true;
      }
    });
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  private void doInitialize() {
    myRootIndex = null;
    if (ourUseRootIndexOnly) {
      return;
    }

    IndexState newState = new IndexState();
    newState.doInitialize(false);
    replaceState(newState);
  }

  private boolean isExcludeRootForModule(@NotNull Module module, VirtualFile excludeRoot) {
    for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
      if (policy.isExcludeRootForModule(module, excludeRoot)) return true;
    }
    return false;
  }

  @NotNull
  private static ContentEntry[] getContentEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getContentEntries();
  }

  @NotNull
  private static OrderEntry[] getOrderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  private static boolean isIgnored(@NotNull VirtualFile f) {
    return FileTypeRegistry.getInstance().isFileIgnored(f);
  }

  @Override
  public DirectoryInfo getInfoForDirectory(@NotNull VirtualFile dir) {
    checkAvailability();
    dispatchPendingEvents();

    if (!(dir instanceof NewVirtualFile)) return null;

    RootIndex rootIndex = getRootIndex();
    DirectoryInfo riInfo = rootIndex != null ? rootIndex.getInfoForDirectory(dir) : null;
    if (ourUseRootIndexOnly) {
      return riInfo;
    }

    DirectoryInfo standardResult = myState.getInfo(((NewVirtualFile)dir).getId());
    assertConsistentResult(dir, riInfo, standardResult);
    if (standardResult != riInfo && standardResult.equals(riInfo) && rootIndex != null) {
      rootIndex.cacheInfos(dir, dir, standardResult);
    }
    return standardResult;
  }

  private <T> T assertConsistentResult(@NotNull Object arg, @Nullable T rootIndexResult, T standardResult) {
    //noinspection ConstantConditions
    if (ourCompareImplementations && !Comparing.equal(rootIndexResult, standardResult)) {
      String msg = "DirectoryIndex differs from RootIndex at " + arg +
                       "\nriInfo         =  " + rootIndexResult +
                       "\nstandardResult =  " + standardResult + 
                       "\nRoot model:";
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        msg += "\nModule " + module.getName();
        for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
          msg += "\n  Content " + entry.getFile();
          for (VirtualFile file : entry.getSourceFolderFiles()) {
            msg += "\n    Source " + file;
          }
          for (VirtualFile file : entry.getExcludeFolderFiles()) {
            msg += "\n    Excluded " + file;
          }
        }
      }
      for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myProject)) {
        for (VirtualFile root : policy.getExcludeRootsForProject()) {
          msg += "\nProject exclude " + root;
        }
      }

      LOG.error(msg);
    }
    return standardResult;
  }

  @Override
  @Nullable
  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo info) {
    if (info.isInModuleSource()) {
      RootIndex rootIndex = getRootIndex();
      JpsModuleSourceRootType<?> riType = rootIndex != null ? rootIndex.getSourceRootType(info) : null;
      if (ourUseRootIndexOnly) {
        return riType;
      }

      return assertConsistentResult(info, riType, myState.getRootTypeById(info.getSourceRootTypeId()));
    }
    return null;
  }

  @Override
  public boolean isProjectExcludeRoot(@NotNull VirtualFile dir) {
    checkAvailability();
    if (!(dir instanceof NewVirtualFile)) return false;

    if (ourUseRootIndexOnly) {
      //noinspection ConstantConditions
      return getRootIndex().isProjectExcludeRoot(dir);
    }

    //noinspection UnnecessaryLocalVariable
    boolean standardResult = myState.myProjectExcludeRoots.contains(((NewVirtualFile)dir).getId());
/*
    RootIndex rootIndex = getRootIndex();
    Boolean riResult = rootIndex != null ? rootIndex.isProjectExcludeRoot(dir) : null;
    assertConsistentResult(dir, riResult, standardResult);
*/
    return standardResult;
  }

  private VirtualFile findFileById(int dir) {
    return myPersistence.findFileById(dir);
  }

  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    checkAvailability();
    if (!(dir instanceof NewVirtualFile)) return null;

    RootIndex rootIndex = getRootIndex();
    String riResult = rootIndex != null ? rootIndex.getPackageName(dir) : null;
    if (ourUseRootIndexOnly) {
      return riResult;
    }

    return assertConsistentResult(dir, riResult, myState.getPackageNameForDirectory((NewVirtualFile)dir));
  }

  private static String decodePackageName(@NotNull int[] interned) {
    if (interned.length == 0) {
      return "";
    }

    StringBuilder result = new StringBuilder(interned[0]);
    for (int i = 1; i < interned.length; i++) {
      if (i > 1) {
        result.append('.');
      }
      result.append(FileNameCache.getVFileName(interned[i]));
    }
    return result.toString();
  }

  private static int[] internPackageName(@Nullable String packageName, @Nullable TObjectIntHashMap<String> alreadyEnumerated) {
    if (packageName == null) {
      return null;
    }

    if (packageName.isEmpty()) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int dotCount = StringUtil.countChars(packageName, '.');
    int[] result = new int[dotCount + 2];
    result[0] = packageName.length();

    int tokenStart = 0;
    int tokenIndex = 0;
    while (tokenStart < packageName.length()) {
      int tokenEnd = packageName.indexOf('.', tokenStart);
      if (tokenEnd < 0) {
        tokenEnd = packageName.length();
      }
      String nextName = packageName.substring(tokenStart, tokenEnd);
      int internedId = alreadyEnumerated != null ? alreadyEnumerated.get(nextName) : 0;
      if (internedId == 0) {
        internedId = FileNameCache.storeName(nextName);
        if (alreadyEnumerated != null) alreadyEnumerated.put(nextName, internedId);
      }
      result[tokenIndex + 1] = internedId;
      tokenStart = tokenEnd + 1;
      tokenIndex++;
    }
    return result;
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

  private class IndexState {
    private final TIntObjectHashMap<Set<String>> myExcludeRootsMap = new TIntObjectHashMap<Set<String>>();
    private final TIntHashSet myProjectExcludeRoots = new TIntHashSet();
    private final TIntObjectHashMap<DirectoryInfo> myDirToInfoMap = new TIntObjectHashMap<DirectoryInfo>();
    private final TObjectIntHashMap<int[]> myPackageNameToDirsMap = new TObjectIntHashMap<int[]>(INT_ARRAY_STRATEGY);
    private final List<int[]> multiDirPackages = new ArrayList<int[]>(Arrays.asList(new int[]{-1}));
    private final TIntObjectHashMap<int[]> myDirToPackageName = new TIntObjectHashMap<int[]>();
    private final TObjectIntHashMap<JpsModuleSourceRootType<?>> myRootTypeId = new TObjectIntHashMap<JpsModuleSourceRootType<?>>();
    private final List<JpsModuleSourceRootType<?>> myRootTypes = new ArrayList<JpsModuleSourceRootType<?>>();
    private volatile boolean writable = true;

    private IndexState() {
    }

    @Nullable
    private int[] getDirsForPackage(@NotNull int[] packageName) {
      assertNotWritable();
      int i = myPackageNameToDirsMap.get(packageName);
      return i == 0 ? null : i > 0 ? new int[]{i} : multiDirPackages.get(-i);
    }

    private void removeDirFromPackage(@NotNull int[] packageName, int dirId) {
      assertWritable();
      int i = myPackageNameToDirsMap.get(packageName);
      int[] oldPackageDirs = i == 0 ? null : i > 0 ? new int[]{i} : multiDirPackages.get(-i);
      assert oldPackageDirs != null;
      int index = ArrayUtil.find(oldPackageDirs, dirId);
      assert index != -1;
      oldPackageDirs = ArrayUtil.remove(oldPackageDirs, index);

      if (oldPackageDirs.length == 0) {
        myPackageNameToDirsMap.remove(packageName);
        if (i < 0) {
          multiDirPackages.set(-i, null);
        }
      }
      else {
        assert i < 0 : i;
        multiDirPackages.set(-i, oldPackageDirs);
      }
    }

    private void addDirToPackage(@NotNull int[] packageName, int dirId) {
      assertWritable();
      assert dirId > 0;

      int i = myPackageNameToDirsMap.get(packageName);

      if (i < 0) {
        // add another dir to the list of existing dirs
        int[] ids = multiDirPackages.get(-i);
        int[] newIds = ids == null ? new int[]{dirId} : ArrayUtil.append(ids, dirId);
        multiDirPackages.set(-i, newIds);
      }
      else if (i > 0) {
        // two dirs instead of one
        int newIndex = multiDirPackages.size();
        multiDirPackages.add(new int[]{i, dirId});
        myPackageNameToDirsMap.put(packageName, -newIndex);
      }
      else {
        // create new dir mapping
        myPackageNameToDirsMap.put(packageName, dirId);
      }
    }

    @NotNull
    private DirectoryInfo getOrCreateDirInfo(int dirId) {
      DirectoryInfo info = getInfo(dirId);
      if (info == null) {
        info = DirectoryInfo.createNew();
        storeInfo(info, dirId);
      }
      return info;
    }

    @Nullable
    private DirectoryInfo getInfo(int fileId) {
      return myDirToInfoMap.get(fileId);
    }

    private void storeInfo(@NotNull DirectoryInfo info, int id) {
      assertWritable();
      if (CHECK) {
        VirtualFile file = findFileById(id);
        VirtualFile contentRoot = info.getContentRoot();
        if (file != null && contentRoot != null) {
            assert VfsUtilCore.isAncestor(contentRoot, file, false) : "File: "+file+"; Content root: "+contentRoot;
        }
      }
      assert id > 0 : id;
      myDirToInfoMap.put(id, info);
    }

    private void assertAncestorsConsistent() {
      if (CHECK) {
        myDirToInfoMap.forEachEntry(new TIntObjectProcedure<DirectoryInfo>() {
          @Override
          public boolean execute(int id, DirectoryInfo info) {
            VirtualFile file = findFileById(id);
            if (file == null) {
              return true;
            }
            VirtualFile contentRoot = info.getContentRoot();
            if (contentRoot != null) {
              assertAncestor(info, contentRoot, id);
            }
            VirtualFile sourceRoot = info.getSourceRoot();
            if (sourceRoot != null) {
              assertAncestor(info, sourceRoot, id);

              if (contentRoot != null) {
                assert VfsUtilCore.isAncestor(contentRoot, sourceRoot, false) : contentRoot + ";" + sourceRoot;
              }
            }
            return true;
          }
        });
      }
    }

    private void fillMapWithModuleContent(@NotNull NewVirtualFile root,
                                          final Module module,
                                          final NewVirtualFile contentRoot,
                                          @Nullable final ProgressIndicator progress) {
      assertWritable();
      if (!isValid(root)) return;
      final int contentRootId = contentRoot == null ? 0 : contentRoot.getId();
      if (contentRoot != null) {
        assert VfsUtilCore.isAncestor(contentRoot, root, false) : "Root: "+root+"; contentRoot: "+contentRoot;
      }
      VfsUtilCore.visitChildrenRecursively(root, new DirectoryVisitor() {
        @Override
        protected DirectoryInfo updateInfo(@NotNull VirtualFile file) {
          if (progress != null) {
            progress.checkCanceled();
          }
          if (isExcluded(contentRootId, file)) return null;
          if (isIgnored(file)) return null;

          DirectoryInfo info = getOrCreateDirInfo(((NewVirtualFile)file).getId());

          if (info.getModule() != null) { // module contents overlap
            VirtualFile dir = file.getParent();
            DirectoryInfo parentInfo = dir == null ? null : getInfo(((NewVirtualFile)dir).getId());
            if (parentInfo == null || !info.getModule().equals(parentInfo.getModule())) return null;
          }

          return info;
        }

        @Override
        protected void afterChildrenVisited(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
          with(((NewVirtualFile)file).getId(), info, module, contentRoot, null, null, 0, null);
        }
      });
    }

    @NotNull
    private DirectoryInfo with(int id,
                               @NotNull DirectoryInfo info,
                               Module module,
                               VirtualFile contentRoot,
                               VirtualFile sourceRoot,
                               VirtualFile libraryClassRoot,
                               int sourceRootTypeData,
                               OrderEntry[] orderEntries) {
      if (contentRoot != null) {
        assertAncestor(info, contentRoot, id);
      }
      if (sourceRoot instanceof NewVirtualFile) {
        VirtualFile root = contentRoot == null ? info.getContentRoot() : contentRoot;
        if (root != null) {
          assertAncestor(info, root, ((NewVirtualFile)sourceRoot).getId());
        }
      }
      DirectoryInfo newInfo = info.with(module, contentRoot, sourceRoot, libraryClassRoot, sourceRootTypeData, orderEntries);
      storeInfo(newInfo, id);
      return newInfo;
    }

    @Nullable
    private String getPackageNameForDirectory(NewVirtualFile dir) {
      int[] interned = myDirToPackageName.get(dir.getId());
      return interned == null ? null : decodePackageName(interned);
    }

    private abstract class DirectoryVisitor extends VirtualFileVisitor {
      private final Stack<DirectoryInfo> myDirectoryInfoStack = new Stack<DirectoryInfo>();

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory()) return false;
        DirectoryInfo info = updateInfo(file);
        if (info != null) {
          myDirectoryInfoStack.push(info);
          return true;
        }
        return false;
      }

      @Override
      public void afterChildrenVisited(@NotNull VirtualFile file) {
        afterChildrenVisited(file, myDirectoryInfoStack.pop());
      }

      @Nullable
      protected abstract DirectoryInfo updateInfo(@NotNull VirtualFile file);

      protected void afterChildrenVisited(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {}
    }

    private boolean isExcluded(int root, @NotNull VirtualFile dir) {
      if (root == 0) return false;
      Set<String> excludes = myExcludeRootsMap.get(root);
      return excludes != null && excludes.contains(dir.getUrl());
    }

    private void initModuleContents(@NotNull Module module, boolean reverseAllSets, @NotNull ProgressIndicator progress) {
      assertWritable();
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.module.content.progress", module.getName()));

      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] contentRoots = rootManager.getContentRoots();
      if (reverseAllSets) {
        contentRoots = ArrayUtil.reverseArray(contentRoots);
      }

      for (final VirtualFile contentRoot : contentRoots) {
        if (contentRoot instanceof NewVirtualFile) {
          fillMapWithModuleContent((NewVirtualFile)contentRoot, module, (NewVirtualFile)contentRoot, progress);
        }
      }
    }

    private void initModuleSources(@NotNull Module module, boolean reverseAllSets, @NotNull ProgressIndicator progress,
                                   @Nullable TObjectIntHashMap<String> interned) {
      assertWritable();
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.module.sources.progress", module.getName()));

      ContentEntry[] contentEntries = getContentEntries(module);

      if (reverseAllSets) {
        contentEntries = ArrayUtil.reverseArray(contentEntries);
      }

      for (ContentEntry contentEntry : contentEntries) {
        VirtualFile contentRoot = contentEntry.getFile();
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        if (reverseAllSets) {
          sourceFolders = ArrayUtil.reverseArray(sourceFolders);
        }
        for (SourceFolder sourceFolder : sourceFolders) {
          VirtualFile dir = sourceFolder.getFile();
          if (dir instanceof NewVirtualFile && contentRoot instanceof NewVirtualFile) {
            int rootTypeId = getRootTypeId(sourceFolder);
            fillMapWithModuleSource(module, (NewVirtualFile)contentRoot, (NewVirtualFile)dir, sourceFolder.getPackagePrefix(),
                                    (NewVirtualFile)dir, rootTypeId, progress, interned);
          }
        }
      }
    }

    private int getRootTypeId(@NotNull SourceFolder sourceFolder) {
      JpsModuleSourceRootType<?> rootType = sourceFolder.getRootType();
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
    private JpsModuleSourceRootType<?> getRootTypeById(int id) {
      if (id >= myRootTypes.size()) return null;
      return myRootTypes.get(id);
    }

    private void fillMapWithModuleSource(@NotNull final Module module,
                                         @NotNull final NewVirtualFile contentRoot,
                                         @NotNull final NewVirtualFile dir,
                                         @NotNull final String packageName,
                                         @NotNull final NewVirtualFile sourceRoot,
                                         final int rootTypeId,
                                         @Nullable final ProgressIndicator progress,
                                         final @Nullable TObjectIntHashMap<String> interned) {
      assertWritable();
      if (!isValid(dir)) return;
      assert VfsUtilCore.isAncestor(sourceRoot, dir, false) :
        "SourceRoot: "+sourceRoot+" ("+sourceRoot.getFileSystem()+"); dir: "+dir+" ("+dir.getFileSystem()+")";
      VfsUtilCore.visitChildrenRecursively(dir, new DirectoryVisitor() {
        private final Stack<String> myPackages = new Stack<String>();

        @Override
        protected DirectoryInfo updateInfo(@NotNull VirtualFile file) {
          if (progress != null) {
            progress.checkCanceled();
          }
          int id = ((NewVirtualFile)file).getId();
          DirectoryInfo info = getInfo(id);
          if (info == null) return null;
          if (!module.equals(info.getModule())) return null;
          if (!contentRoot.equals(info.getContentRoot())) return null;

          if (info.isInModuleSource()) { // module sources overlap
            if (isAnotherRoot(id)) return null; // another source root starts here
          }

          assert VfsUtilCore.isAncestor(dir, file, false) :
            "dir: " + dir + " (" + dir.getFileSystem() + "); file: " + file + " (" + file.getFileSystem() + ")";

          int flag = DirectoryInfo.createSourceRootTypeData(true, info.isInLibrarySource(), rootTypeId);
          info = with(id, info, null, null, sourceRoot, null, flag, null);

          String currentPackage = myPackages.isEmpty() ? packageName : getPackageNameForSubdir(myPackages.peek(), file.getName());
          myPackages.push(currentPackage);
          setPackageName(id, internPackageName(currentPackage, interned));
          return info;
        }

        @Override
        protected void afterChildrenVisited(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
          super.afterChildrenVisited(file, info);
          myPackages.pop();
        }
      });
    }

    private boolean isAnotherRoot(int id) {
      return myDirToPackageName.get(id) == ArrayUtil.EMPTY_INT_ARRAY;
    }

    private void initLibrarySources(@NotNull Module module,
                                    @NotNull ProgressIndicator progress,
                                    @Nullable TObjectIntHashMap<String> interned,
                                    Map<Library, TIntHashSet> libraryExcludedRoots) {
      assertWritable();
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.library.sources.progress", module.getName()));

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          VirtualFile[] sourceRoots = ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(OrderRootType.SOURCES);
          TIntHashSet excludedRoots = getExcludedRootsOfLibrary((LibraryOrSdkOrderEntry)orderEntry, libraryExcludedRoots);
          for (final VirtualFile sourceRoot : sourceRoots) {
            if (sourceRoot instanceof NewVirtualFile) {
              fillMapWithLibrarySources((NewVirtualFile)sourceRoot, "", (NewVirtualFile)sourceRoot, progress, interned, excludedRoots);
            }
          }
        }
      }
    }

    private void fillMapWithLibrarySources(@NotNull final NewVirtualFile dir,
                                           @Nullable final String packageName,
                                           @NotNull final NewVirtualFile sourceRoot,
                                           @Nullable final ProgressIndicator progress,
                                           @Nullable final TObjectIntHashMap<String> interned,
                                           @Nullable final TIntHashSet excludedRoots) {
      assertWritable();
      if (!isValid(dir)) return;
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<String>() {
        { setValueForChildren(packageName); }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (progress != null) progress.checkCanceled();
          int dirId = ((NewVirtualFile)file).getId();
          if (!file.isDirectory() && dirId != dir.getId() || isIgnored(file)) return false;
          if (excludedRoots != null && excludedRoots.contains(dirId)) return false;
          DirectoryInfo info = getOrCreateDirInfo(dirId);

          if (info.isInLibrarySource()) { // library sources overlap
            if (isAnotherRoot(dirId)) return false; // another library source root starts here
          }

          int data = DirectoryInfo.createSourceRootTypeData(info.isInModuleSource(), true, info.getSourceRootTypeId());
          with(dirId, info, null, null, sourceRoot, null, data, null);

          final String packageName = getCurrentValue();
          final String newPackageName = Comparing.equal(file, dir) ? packageName : getPackageNameForSubdir(packageName, file.getName());
          setPackageName(dirId, internPackageName(newPackageName, interned));
          setValueForChildren(newPackageName);

          return true;
        }
      });
    }

    private void initLibraryClasses(@NotNull Module module,
                                    @NotNull ProgressIndicator progress,
                                    @Nullable TObjectIntHashMap<String> interned,
                                    @Nullable Map<Library, TIntHashSet> libraryExcludedRoots) {
      assertWritable();
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.library.classes.progress", module.getName()));

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          VirtualFile[] classRoots = ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
          TIntHashSet excludedRoots = getExcludedRootsOfLibrary((LibraryOrSdkOrderEntry)orderEntry, libraryExcludedRoots);
          for (final VirtualFile classRoot : classRoots) {
            if (classRoot instanceof NewVirtualFile) {
              fillMapWithLibraryClasses((NewVirtualFile)classRoot, "", (NewVirtualFile)classRoot, progress, interned, excludedRoots);
            }
          }
        }
      }
    }

    private void fillMapWithLibraryClasses(@NotNull final NewVirtualFile dir,
                                           @NotNull final String packageName,
                                           @NotNull final NewVirtualFile classRoot,
                                           @Nullable final ProgressIndicator progress,
                                           @Nullable final TObjectIntHashMap<String> interned,
                                           final TIntHashSet excludedRoots) {
      assertWritable();
      if (!isValid(dir)) return;
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<String>() {
        { setValueForChildren(packageName); }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (progress != null) progress.checkCanceled();
          if (!file.isDirectory() && !Comparing.equal(file, dir) || isIgnored(file)) return false;

          int dirId = ((NewVirtualFile)file).getId();
          if (excludedRoots != null && excludedRoots.contains(dirId)) return false;
          DirectoryInfo info = getOrCreateDirInfo(dirId);

          if (info.hasLibraryClassRoot()) { // library classes overlap
            if (isAnotherRoot(dirId)) return false; // another library root starts here
          }

          info = with(dirId, info, null, null, null, classRoot, 0, null);

          final String packageName = getCurrentValue();
          final String childPackageName = Comparing.equal(file, dir) ? packageName : getPackageNameForSubdir(packageName, file.getName());
          if (!info.isInModuleSource() && !info.isInLibrarySource()) {
            setPackageName(dirId, internPackageName(childPackageName, interned));
          }
          setValueForChildren(childPackageName);

          return true;
        }
      });
    }

    @Nullable
    private TIntHashSet getExcludedRootsOfLibrary(LibraryOrSdkOrderEntry orderEntry, Map<Library, TIntHashSet> libraryExcludedRoots) {
      if (orderEntry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          TIntHashSet cached = libraryExcludedRoots.get(library);
          if (cached != null) return cached;

          VirtualFile[] files = ((LibraryEx)library).getExcludedRoots();
          if (files.length > 0) {
            TIntHashSet set = new TIntHashSet();
            for (VirtualFile file : files) {
              if (file instanceof NewVirtualFile) {
                set.add(((NewVirtualFile)file).getId());
              }
            }
            libraryExcludedRoots.put(library, set);
            return set;
          }
        }
      }
      return null;
    }

    private void initOrderEntries(@NotNull Module module,
                                  @NotNull MultiMap<VirtualFile, OrderEntry> depEntries,
                                  @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                  @NotNull MultiMap<VirtualFile, OrderEntry> libSourceRootEntries,
                                  @NotNull ProgressIndicator progress) {
      assertWritable();
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
          OrderEntry[] oneEntryList = {orderEntry};
          Module entryModule = orderEntry.getOwnerModule();

          VirtualFile[] sourceRoots = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getSourceRoots();
          for (VirtualFile sourceRoot : sourceRoots) {
            if (sourceRoot instanceof NewVirtualFile) {
              fillMapWithOrderEntries((NewVirtualFile)sourceRoot, oneEntryList, entryModule, null, null, null, progress);
            }
          }
        }
        else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);
          for (VirtualFile classRoot : classRoots) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
          VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
        }
      }
    }

    private void fillMapWithOrderEntries(@NotNull MultiMap<VirtualFile, OrderEntry> depEntries,
                                         @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                         @NotNull MultiMap<VirtualFile, OrderEntry> libSourceRootEntries,
                                         @NotNull ProgressIndicator progress) {
      assertWritable();
      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : depEntries.entrySet()) {
        VirtualFile vRoot = mapEntry.getKey();
        Collection<OrderEntry> entries = mapEntry.getValue();
        if (vRoot instanceof NewVirtualFile) {
          fillMapWithOrderEntries((NewVirtualFile)vRoot, toSortedArray(entries), null, null, null, null, progress);
        }
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        if (vRoot instanceof NewVirtualFile) {
          fillMapWithOrderEntries((NewVirtualFile)vRoot, toSortedArray(entries), null, (NewVirtualFile)vRoot, null, null, progress);
        }
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        if (vRoot instanceof NewVirtualFile) {
          fillMapWithOrderEntries((NewVirtualFile)vRoot, toSortedArray(entries), null, null, (NewVirtualFile)vRoot, null, progress);
        }
      }
    }

    private void setPackageName(int dirId, @Nullable int[] newPackageName) {
      assertWritable();
      int[] oldPackageName = myDirToPackageName.get(dirId);
      if (oldPackageName != null) {
        removeDirFromPackage(oldPackageName, dirId);
      }

      if (newPackageName == null) {
        myDirToPackageName.remove(dirId);
      }
      else {
        addDirToPackage(newPackageName, dirId);

        myDirToPackageName.put(dirId, newPackageName);
      }
    }

    // orderEntries must be sorted BY_OWNER_MODULE
    private void fillMapWithOrderEntries(@NotNull final NewVirtualFile root,
                                         @NotNull final OrderEntry[] orderEntries,
                                         @Nullable final Module module,
                                         @Nullable final NewVirtualFile libraryClassRoot,
                                         @Nullable final NewVirtualFile librarySourceRoot,
                                         @Nullable final DirectoryInfo parentInfo,
                                         @Nullable final ProgressIndicator progress) {
      assertWritable();
      if (!isValid(root)) return;
      VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
        private final Stack<OrderEntry[]> myEntries = new Stack<OrderEntry[]>();

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (progress != null) {
            progress.checkCanceled();
          }
          if (!file.isDirectory() && !file.equals(root) || isIgnored(file)) return false;

          int fileId = ((NewVirtualFile)file).getId();
          DirectoryInfo info = getInfo(fileId); // do not create it here!
          if (info == null) return false;

          if (module != null) {
            if (info.getModule() != module || !info.isInModuleSource()) return false;
          }
          else if (libraryClassRoot != null) {
            if (!libraryClassRoot.equals(info.getLibraryClassRoot()) || info.isInModuleSource()) return false;
          }
          else if (librarySourceRoot != null) {
            if (!info.isInLibrarySource()
                || !librarySourceRoot.equals(info.getSourceRoot())
                || info.hasLibraryClassRoot()) return false;
          }

          OrderEntry[] oldParentEntries = myEntries.isEmpty() ? null : myEntries.peek();
          OrderEntry[] oldEntries = info.getOrderEntries();
          myEntries.push(oldEntries);

          OrderEntry[] newOrderEntries = info.calcNewOrderEntries(orderEntries, parentInfo, oldParentEntries);
          with(fileId, info, null, null, null, null, 0, newOrderEntries);

          return true;
        }

        @Override
        public void afterChildrenVisited(@NotNull VirtualFile file) {
          myEntries.pop();
        }
      });
    }

    private void doInitialize(boolean reverseAllSets/* for testing order independence*/) {
      assertWritable();
      assertAncestorsConsistent();
      ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
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

      TObjectIntHashMap<String> interned = new TObjectIntHashMap<String>(100);

      // Important! Because module's contents may overlap,
      // first modules should be marked and only after that sources markup
      // should be added. (src markup depends on module markup)
      IdentityHashMap<Library, TIntHashSet> libraryExcludedRoots = new IdentityHashMap<Library, TIntHashSet>();
      for (Module module : modules) {
        initModuleSources(module, reverseAllSets, progress, interned);
        initLibrarySources(module, progress, interned, libraryExcludedRoots);
        initLibraryClasses(module, progress , interned, libraryExcludedRoots);
      }

      progress.checkCanceled();
      progress.setText2("");

      assertAncestorsConsistent();
      MultiMap<VirtualFile, OrderEntry> depEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      for (Module module : modules) {
        initOrderEntries(module, depEntries, libClassRootEntries, libSourceRootEntries, progress);
      }
      fillMapWithOrderEntries(depEntries, libClassRootEntries, libSourceRootEntries, progress);

      internDirectoryInfos();
      progress.popState();
    }

    private void internDirectoryInfos() {
      assertWritable();
      final Map<DirectoryInfo, DirectoryInfo> diInterner = new THashMap<DirectoryInfo, DirectoryInfo>();
      final Map<OrderEntry[], OrderEntry[]> oeInterner = new THashMap<OrderEntry[], OrderEntry[]>(new TObjectHashingStrategy<OrderEntry[]>() {
        @Override
        public int computeHashCode(OrderEntry[] object) {
          return Arrays.hashCode(object);
        }

        @Override
        public boolean equals(OrderEntry[] o1, OrderEntry[] o2) {
          return Arrays.equals(o1, o2);
        }
      });

      assertAncestorsConsistent();
      myDirToInfoMap.transformValues(new TObjectFunction<DirectoryInfo, DirectoryInfo>() {
        @Override
        public DirectoryInfo execute(DirectoryInfo info) {
          DirectoryInfo interned = diInterner.get(info);
          if (interned == null) {
            OrderEntry[] entries = info.getOrderEntries();
            OrderEntry[] internedEntries = oeInterner.get(entries);
            if (internedEntries == null) {
              oeInterner.put(entries, entries);
            }
            else if (internedEntries != entries) {
              info = info.withInternedEntries(internedEntries);
            }
            diInterner.put(info, interned = info);
          }
          return interned;
        }
      });
      assertAncestorsConsistent();
    }

    private void initExcludedDirMap(@NotNull Module[] modules, ProgressIndicator progress) {
      assertWritable();
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.building.exclude.roots.progress"));

      // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
      // exclude root should exclude from its content root and all outer content roots

      for (Module module : modules) {
        for (ContentEntry contentEntry : getContentEntries(module)) {
          VirtualFile contentRoot = contentEntry.getFile();
          if (!(contentRoot instanceof NewVirtualFile)) continue;

          for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
            // Output paths should be excluded (if marked as such) regardless if they're under corresponding module's content root
            if (excludeRoot instanceof NewVirtualFile) {
              if (!FileUtil.startsWith(excludeRoot.getUrl(), contentRoot.getUrl())) {
                if (isExcludeRootForModule(module, excludeRoot)) {
                  putForFileAndAllAncestors((NewVirtualFile)excludeRoot, excludeRoot.getUrl());
                }
                myProjectExcludeRoots.add(((NewVirtualFile)excludeRoot).getId());
              }
            }

          }
          for (String url : contentEntry.getExcludeFolderUrls()) {
            putForFileAndAllAncestors((NewVirtualFile)contentRoot, url);
          }
        }
      }

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        for (VirtualFile file : policy.getExcludeRootsForProject()) {
          if (file instanceof NewVirtualFile) {
            putForFileAndAllAncestors((NewVirtualFile)file, file.getUrl());
            myProjectExcludeRoots.add(((NewVirtualFile)file).getId());
          }
        }
      }
    }

    private void putForFileAndAllAncestors(NewVirtualFile file, String value) {
      assertWritable();
      TIntObjectHashMap <Set<String>> map = myExcludeRootsMap;
      while (file != null) {
        int id = file.getId();
        Set<String> set = map.get(id);
        if (set == null) {
          set = new THashSet<String>();
          map.put(id, set);
        }
        set.add(value);

        file = file.getParent();
      }
    }

    private void assertWritable() {
      assert writable;
    }

    private void assertNotWritable() {
      assert !writable;
    }

    @NotNull
    private IndexState copy(@Nullable final TIntProcedure idFilter) {
      assertNotWritable();
      final IndexState copy = new IndexState();

      myExcludeRootsMap.forEachEntry(new TIntObjectProcedure<Set<String>>() {
        @Override
        public boolean execute(int id, Set<String> urls) {
          if (idFilter == null || idFilter.execute(id)) {
            copy.myExcludeRootsMap.put(id, new THashSet<String>(urls));
          }
          return true;
        }
      });

      copy.myProjectExcludeRoots.addAll(myProjectExcludeRoots.toArray());
      myDirToInfoMap.forEachEntry(new TIntObjectProcedure<DirectoryInfo>() {
        @Override
        public boolean execute(int id, DirectoryInfo info) {
          if (idFilter == null || idFilter.execute(id)) {
            copy.storeInfo(info, id);
          }
          return true;
        }
      });


      copy.multiDirPackages.clear();
      for (int[] dirs : multiDirPackages) {
        if (dirs == null) {
          dirs = ArrayUtil.EMPTY_INT_ARRAY;
        }
        int[] filtered = ContainerUtil.filter(dirs, new TIntProcedure() {
          @Override
          public boolean execute(int id) {
            return id == -1 || copy.getInfo(id) != null && (idFilter == null || idFilter.execute(id));
          }
        });
        copy.multiDirPackages.add(filtered);
      }
      myPackageNameToDirsMap.forEachEntry(new TObjectIntProcedure<int[]>() {
        @Override
        public boolean execute(int[] name, int id) {
          if (id > 0) {
            if (copy.getInfo(id) == null) id = 0;
          }
          else if (id < 0) {
            if (copy.multiDirPackages.get(-id).length == 0) id = 0;
          }
          if (id != 0 && (idFilter == null || idFilter.execute(id))) {
            copy.myPackageNameToDirsMap.put(name, id);
          }
          return true;
        }
      });

      myDirToPackageName.forEachEntry(new TIntObjectProcedure<int[]>() {
        @Override
        public boolean execute(int id, int[] name) {
          if (idFilter == null || idFilter.execute(id)) {
            copy.myDirToPackageName.put(id, name);
          }
          return true;
        }
      });

      copy.myRootTypes.addAll(myRootTypes);
      myRootTypeId.forEachEntry(new TObjectIntProcedure<JpsModuleSourceRootType<?>>() {
        @Override
        public boolean execute(JpsModuleSourceRootType<?> root, int id) {
          copy.myRootTypeId.put(root, id);
          return true;
        }
      });

      return copy;
    }
  }

  private static boolean isValid(@NotNull NewVirtualFile root) {
    return root.getId() > 0;
  }

  @NotNull
  private static OrderEntry[] toSortedArray(@NotNull Collection<OrderEntry> entries) {
    if (entries.isEmpty()) {
      return OrderEntry.EMPTY_ARRAY;
    }
    OrderEntry[] result = entries.toArray(new OrderEntry[entries.size()]);
    Arrays.sort(result, DirectoryInfo.BY_OWNER_MODULE);
    return result;
  }

  private void assertAncestor(@NotNull DirectoryInfo info, @NotNull VirtualFile root, int myId) {
    VirtualFile myFile = findFileById(myId);
    assert myFile.getFileSystem() == root.getFileSystem() :
      myFile.getFileSystem() + ", " + root.getFileSystem() + "; my file: " + myFile + "; root: " + root + "; " +
      myFile.getParent().getPath().equals(root.getPath());
    assert VfsUtilCore.isAncestor(root, myFile, false) :
      "my file: " + myFile + " (" + ((NewVirtualFile)myFile).getId() + ")" + myFile.getClass() + " - " + System.identityHashCode(myFile) +
      "; root: " + root + " (" + ((NewVirtualFile)root).getId() + ")" + root.getClass() + " - " + System.identityHashCode(root) +
      "; equalsToParent:" + (myFile.getParent() == null ? "" : myFile.getParent().getPath()).equals(root.getPath()) +
      "; equalsToRoot:" + myFile.equals(root) +
      "; equalsToRootPath:" + myFile.getPath().equals(root.getPath()) +
      "; my contentRoot: " + info.getContentRoot() +
      "; my sourceRoot: " + info.getSourceRoot() +
      "; my classRoot: " + info.getLibraryClassRoot() +
      "; path is substring: " + FileUtil.isAncestor(root.getPath(), myFile.getPath(), false)
      ;
  }
}
