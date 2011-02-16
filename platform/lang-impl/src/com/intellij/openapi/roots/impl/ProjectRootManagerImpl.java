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
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.stores.BatchUpdateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootManagerImpl");

  @NonNls private static final String PROJECT_JDK_NAME_ATTR = "project-jdk-name";
  @NonNls private static final String PROJECT_JDK_TYPE_ATTR = "project-jdk-type";

  private final ProjectEx myProject;
  private final ProjectFileIndex myProjectFileIndex;

  private final EventDispatcher<ProjectJdkListener> myProjectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener.class);

  private String myProjectSdkName;
  private String myProjectSdkType;

  private final List<CacheUpdater> myRootsChangeUpdaters = new ArrayList<CacheUpdater>();
  private final List<CacheUpdater> myRefreshCacheUpdaters = new ArrayList<CacheUpdater>();

  private long myModificationCount = 0;
  private Set<LocalFileSystem.WatchRequest> myRootsToWatch = new HashSet<LocalFileSystem.WatchRequest>();
  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  private final Map<List<Module>, GlobalSearchScope> myLibraryScopes = new ConcurrentHashMap<List<Module>, GlobalSearchScope>();
  private final Map<String, GlobalSearchScope> myJdkScopes = new HashMap<String, GlobalSearchScope>();
  private final OrderRootsCache myRootsCache;

  private boolean myStartupActivityPerformed = false;

  private final MessageBusConnection myConnection;
  private final BatchUpdateListener myHandler;

  private class BatchSession {
    private int myBatchLevel = 0;
    private boolean myChanged = false;

    private final boolean myFileTypes;

    private BatchSession(final boolean fileTypes) {
      myFileTypes = fileTypes;
    }

    private void levelUp() {
      if (myBatchLevel == 0) {
        myChanged = false;
      }
      myBatchLevel += 1;
    }

    private void levelDown() {
      myBatchLevel -= 1;
      if (myChanged && myBatchLevel == 0) {
        try {
          fireChange();
        }
        finally {
          myChanged = false;
        }
      }
    }

    private boolean fireChange() {
      return fireRootsChanged(myFileTypes);
    }

    private void beforeRootsChanged() {
      if (myBatchLevel == 0 || !myChanged) {
        if (fireBeforeRootsChanged(myFileTypes)) {
          myChanged = true;
        }
      }
    }

    private void rootsChanged() {
      if (myBatchLevel == 0) {
        if (fireChange()) {
          myChanged = false;
        }
      }
    }
  }

  private final BatchSession myRootsChanged = new BatchSession(false);
  private final BatchSession myFileTypesChanged = new BatchSession(true);

  public static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(Project project,
                                FileTypeManager fileTypeManager,
                                DirectoryIndex directoryIndex,
                                StartupManager startupManager) {
    myProject = (ProjectEx)project;
    myConnection = project.getMessageBus().connect(project);
    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
        beforeRootsChange(true);
      }

      public void fileTypesChanged(FileTypeEvent event) {
        rootsChanged(true);
      }
    });

    VirtualFileManager.getInstance().addVirtualFileManagerListener(new VirtualFileManagerAdapter() {
      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        doUpdateOnRefresh();
      }
    },project);

    myRootsCache = new OrderRootsCache(project);
    myProjectFileIndex = new ProjectFileIndexImpl(myProject, directoryIndex, fileTypeManager);
    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        myStartupActivityPerformed = true;
      }
    });

    myHandler = new BatchUpdateListener() {
      public void onBatchUpdateStarted() {
        myRootsChanged.levelUp();
        myFileTypesChanged.levelUp();
      }

      public void onBatchUpdateFinished() {
        myRootsChanged.levelDown();
        myFileTypesChanged.levelDown();
      }
    };

    myConnection.subscribe(VirtualFilePointerListener.TOPIC, new MyVirtualFilePointerListener());
  }

  public void registerRootsChangeUpdater(CacheUpdater updater) {
    myRootsChangeUpdaters.add(updater);
  }

  public void unregisterRootsChangeUpdater(CacheUpdater updater) {
    boolean removed = myRootsChangeUpdaters.remove(updater);
    LOG.assertTrue(removed);
  }

  @Override
  public void registerRefreshUpdater(CacheUpdater updater) {
    myRefreshCacheUpdaters.add(updater);
  }

  @Override
  public void unregisterRefreshUpdater(CacheUpdater updater) {
    boolean removed = myRefreshCacheUpdaters.remove(updater);
    LOG.assertTrue(removed);
  }

  public void multiCommit(ModifiableRootModel[] rootModels) {
    ModuleRootManagerImpl.multiCommit(rootModels, ModuleManager.getInstance(myProject).getModifiableModel());
  }

  public void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels) {
    ModuleRootManagerImpl.multiCommit(rootModels, moduleModel);
  }

  public VirtualFilePointerListener getVirtualFilePointerListener() {
    return null;
  }

  @NotNull
  public ProjectFileIndex getFileIndex() {
    return myProjectFileIndex;
  }

  private final Map<ModuleRootListener, MessageBusConnection> myListenerAdapters = new HashMap<ModuleRootListener, MessageBusConnection>();

  public void addModuleRootListener(final ModuleRootListener listener) {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    myListenerAdapters.put(listener, connection);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
  }

  public void addModuleRootListener(ModuleRootListener listener, Disposable parentDisposable) {
    final MessageBusConnection connection = myProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
  }

  public void removeModuleRootListener(ModuleRootListener listener) {
    final MessageBusConnection connection = myListenerAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getModules();
    for (Module module : modules) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, contentRoots);
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  public VirtualFile[] getContentSourceRoots() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getModules();
    for (Module module : modules) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      ContainerUtil.addAll(result, sourceRoots);
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  public VirtualFile[] getFilesFromAllModules(OrderRootType type) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getFiles(type);
      ContainerUtil.addAll(result, files);
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ProjectOrderEnumerator(myProject, myRootsCache);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules) {
    return new ModulesOrderEnumerator(myProject, modules);
  }

  public VirtualFile[] getContentRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, files);
    }
    result.add(myProject.getBaseDir());
    return VfsUtil.toVirtualFileArray(result);
  }

  public Sdk getProjectSdk() {
    if (myProjectSdkName != null) {
      return ProjectJdkTable.getInstance().findJdk(myProjectSdkName, myProjectSdkType);
    }
    else {
      return null;
    }
  }

  public String getProjectSdkName() {
    return myProjectSdkName;
  }

  public void setProjectSdk(Sdk sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (sdk != null) {
      myProjectSdkName = sdk.getName();
      myProjectSdkType = sdk.getSdkType().getName();
    }
    else {
      myProjectSdkName = null;
      myProjectSdkType = null;
    }
    mergeRootsChangesDuring(new Runnable() {
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  public void setProjectSdkName(String name) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectSdkName = name;

    mergeRootsChangesDuring(new Runnable() {
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  public void addProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.addListener(listener);
  }

  public void removeProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.removeListener(listener);
  }

  public void projectOpened() {
    addRootsToWatch();
    AppListener applicationListener = new AppListener();
    ApplicationManager.getApplication().addApplicationListener(applicationListener, myProject);
  }

  public void projectClosed() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
  }

  @NotNull
  public String getComponentName() {
    return "ProjectRootManager";
  }

  public void initComponent() {
    myConnection.subscribe(BatchUpdateListener.TOPIC, myHandler);
  }

  public void disposeComponent() {
    myJdkTableMultiListener = null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.readExternal(element);
    }
    myProjectSdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR);
    myProjectSdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_VERSION, "2");
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.writeExternal(element);
    }
    if (myProjectSdkName != null) {
      element.setAttribute(PROJECT_JDK_NAME_ATTR, myProjectSdkName);
    }
    if (myProjectSdkType != null) {
      element.setAttribute(PROJECT_JDK_TYPE_ATTR, myProjectSdkType);
    }
  }

  private boolean myMergedCallStarted = false;
  private boolean myMergedCallHasRootChange = false;
  private int myRootsChangesDepth = 0;

  public void mergeRootsChangesDuring(@NotNull Runnable runnable) {
    if (getBatchSession(false).myBatchLevel == 0 && !myMergedCallStarted) {
      LOG.assertTrue(myRootsChangesDepth == 0,
                     "Merged rootsChanged not allowed inside rootsChanged, rootsChanged level == " + myRootsChangesDepth);
      myMergedCallStarted = true;
      myMergedCallHasRootChange = false;
      try {
        runnable.run();
      }
      finally {
        if (myMergedCallHasRootChange) {
          LOG.assertTrue(myRootsChangesDepth == 1, "myMergedCallDepth = " + myRootsChangesDepth);
          getBatchSession(false).rootsChanged();
        }
        myMergedCallStarted = false;
        myMergedCallHasRootChange = false;
      }
    }
    else {
      runnable.run();
    }
  }

  private void clearScopesCaches() {
    clearScopesCachesForModules();
    myJdkScopes.clear();
    myLibraryScopes.clear();
  }

  public void clearScopesCachesForModules() {
    myRootsCache.clearCache();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).dropCaches();
      ((ModuleImpl)module).clearScopesCache();
    }
  }

  private void beforeRootsChange(boolean filetypes) {
    if (myProject.isDisposed()) return;
    getBatchSession(filetypes).beforeRootsChanged();
  }

  public void makeRootsChange(@NotNull Runnable runnable, boolean filetypes, boolean fireEvents) {
    if (myProject.isDisposed()) return;
    BatchSession session = getBatchSession(filetypes);
    if (fireEvents) session.beforeRootsChanged();
    try {
      runnable.run();
    }
    finally {
      if (fireEvents) session.rootsChanged();
    }
  }

  private void rootsChanged(boolean filetypes) {
    getBatchSession(filetypes).rootsChanged();
  }

  private BatchSession getBatchSession(final boolean filetypes) {
    return filetypes ? myFileTypesChanged : myRootsChanged;
  }

  private boolean isFiringEvent = false;

  private boolean fireBeforeRootsChanged(boolean filetypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    if (myRootsChangesDepth++ == 0) {
      if (myMergedCallStarted) {
        myMergedCallHasRootChange = true;
        myRootsChangesDepth++; // blocks all firing until finishRootsChangedOnDemand
      }
      isFiringEvent = true;
      try {
        myProject.getMessageBus()
          .syncPublisher(ProjectTopics.PROJECT_ROOTS)
          .beforeRootsChange(new ModuleRootEventImpl(myProject, filetypes));
      }
      finally {
        isFiringEvent= false;
      }
      return true;
    }

    return false;
  }

  private boolean fireRootsChanged(boolean filetypes) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    myRootsChangesDepth--;
    if (myRootsChangesDepth > 0) return false;

    clearScopesCaches();

    myModificationCount++;

    isFiringEvent = true;
    try {
      myProject.getMessageBus()
        .syncPublisher(ProjectTopics.PROJECT_ROOTS)
        .rootsChanged(new ModuleRootEventImpl(myProject, filetypes));
    }
    finally {
      isFiringEvent = false;
    }

    doSynchronizeRoots();

    addRootsToWatch();

    return true;
  }

  public Project getProject() {
    return myProject;
  }

  private static class LibrariesOnlyScope extends GlobalSearchScope {
    private final GlobalSearchScope myOriginal;

    private LibrariesOnlyScope(final GlobalSearchScope original) {
      super(original.getProject());
      myOriginal = original;
    }

    public boolean contains(VirtualFile file) {
      return myOriginal.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return myOriginal.compare(file1, file2);
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }

  public GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn) {
    GlobalSearchScope scope = myLibraryScopes.get(modulesLibraryIsUsedIn);
    if (scope == null) {
      if (!modulesLibraryIsUsedIn.isEmpty()) {
        scope = new LibraryRuntimeClasspathScope(myProject, modulesLibraryIsUsedIn);
      }
      else {
        scope = new LibrariesOnlyScope(GlobalSearchScope.allScope(myProject));
      }
      myLibraryScopes.put(modulesLibraryIsUsedIn, scope);
    }
    return scope;
  }

  public GlobalSearchScope getScopeForJdk(final JdkOrderEntry jdkOrderEntry) {
    final String jdk = jdkOrderEntry.getJdkName();
    if (jdk == null) return GlobalSearchScope.allScope(myProject);
    GlobalSearchScope scope = myJdkScopes.get(jdk);
    if (scope == null) {
      scope = new JdkScope(myProject, jdkOrderEntry);
      myJdkScopes.put(jdk, scope);
    }
    return scope;
  }

  private void doSynchronizeRoots() {
    if (!myStartupActivityPerformed) return;
    DumbServiceImpl.getInstance(myProject).queueCacheUpdate(myRootsChangeUpdaters);
  }

  private void doUpdateOnRefresh() {
    if (ApplicationManager.getApplication().isUnitTestMode() && (!myStartupActivityPerformed || myProject.isDisposed())) {
      return; // in test mode suppress addition to a queue unless project is properly initialized
    }
    DumbServiceImpl.getInstance(myProject).queueCacheUpdate(myRefreshCacheUpdaters);
  }

  private void addRootsToWatch() {
    final Set<String> rootPaths = getAllRoots(false);
    if (rootPaths == null) return;

    final Set<LocalFileSystem.WatchRequest> newRootsToWatch = LocalFileSystem.getInstance().addRootsToWatch(rootPaths, true);

    //remove old requests after adding new ones, helps avoiding unnecessary synchronizations
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
    myRootsToWatch = newRootsToWatch;
  }

  @Nullable
  private Set<String> getAllRoots(boolean includeSourceRoots) {
    if (myProject.isDefault()) return null;

    final Set<String> rootPaths = new HashSet<String>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final String[] contentRootUrls = moduleRootManager.getContentRootUrls();
      rootPaths.addAll(getRootsToTrack(contentRootUrls));
      if (includeSourceRoots) {
        final String[] sourceRootUrls = moduleRootManager.getSourceRootUrls();
        rootPaths.addAll(getRootsToTrack(sourceRootUrls));
      }
      rootPaths.add(module.getModuleFilePath());
    }

    for (WatchedRootsProvider extension : Extensions.getExtensions(WatchedRootsProvider.EP_NAME, myProject)) {
      rootPaths.addAll(extension.getRootsToWatch());
    }

    final String projectFile = myProject.getStateStore().getProjectFilePath();
    rootPaths.add(projectFile);
    final VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null) {
      rootPaths.add(baseDir.getPath());
    }
    // No need to add workspace file separately since they're definitely on same directory with ipr.

    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
            rootPaths.addAll(getRootsToTrack(library, orderRootType));
          }
        }
        else if (entry instanceof JdkOrderEntry) {
          for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
            rootPaths.addAll(getRootsToTrack(entry, orderRootType));
          }
        }
      }
    }

    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final String explodedDirectory = moduleRootManager.getExplodedDirectoryUrl();
      if (explodedDirectory != null) {
        rootPaths.add(extractLocalPath(explodedDirectory));
      }
    }

    return rootPaths;
  }

  private static Collection<String> getRootsToTrack(final Library library, final OrderRootType rootType) {
    return library != null ? getRootsToTrack(library.getUrls(rootType)) : Collections.<String>emptyList();
  }

  private static Collection<String> getRootsToTrack(final OrderEntry library, final OrderRootType rootType) {
    return library != null ? getRootsToTrack(library.getUrls(rootType)) : Collections.<String>emptyList();
  }

  private static List<String> getRootsToTrack(final String[] urls) {
    final List<String> result = new ArrayList<String>(urls.length);
    for (String url : urls) {
      if (url != null) {
        final String protocol = VirtualFileManager.extractProtocol(url);
        if (protocol == null || JarFileSystem.PROTOCOL.equals(protocol) || LocalFileSystem.PROTOCOL.equals(protocol)) {
          result.add(extractLocalPath(url));
        }
      }
    }

    return result;
  }

  public static String extractLocalPath(final String url) {
    final String path = VfsUtil.urlToPath(url);
    final int jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (jarSeparatorIndex > 0) {
      return path.substring(0, jarSeparatorIndex);
    }
    return path;
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  void addRootSetChangedListener(RootProvider.RootSetChangedListener rootSetChangedListener, final RootProvider provider) {
    RootSetChangedMulticaster multicaster = myRegisteredRootProviderListeners.get(provider);
    if (multicaster == null) {
      multicaster = new RootSetChangedMulticaster(provider);
    }
    multicaster.addListener(rootSetChangedListener);
  }

  void removeRootSetChangedListener(RootProvider.RootSetChangedListener rootSetChangedListener, final RootProvider provider) {
    RootSetChangedMulticaster multicaster = myRegisteredRootProviderListeners.get(provider);
    if (multicaster != null) {
      multicaster.removeListener(rootSetChangedListener);
    }
  }

  private class MyVirtualFilePointerListener implements VirtualFilePointerListener {
    public void beforeValidityChanged(VirtualFilePointer[] pointers) {
      if (!myProject.isDisposed()) {
        if (myInsideRefresh == 0) {
          if (affectsRoots(pointers)) {
            beforeRootsChange(false);
          }
        }
        else if (!myPointerChangesDetected) {
          //this is the first pointer changing validity
          if (affectsRoots(pointers)) {
            myPointerChangesDetected = true;
            myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).beforeRootsChange(new ModuleRootEventImpl(myProject, false));
          }
        }
      }
    }

    public void validityChanged(VirtualFilePointer[] pointers) {
      if (!myProject.isDisposed()) {
        if (myInsideRefresh > 0) {
          clearScopesCaches();
        }
        else {
          if (affectsRoots(pointers)) {
            rootsChanged(false);
          }
        }
      }
    }
  }

  private boolean affectsRoots(VirtualFilePointer[] pointers) {
    Set<String> roots = getAllRoots(true);
    if (roots == null) return false;

    for (VirtualFilePointer pointer : pointers) {
      if (roots.contains(url2path(pointer.getUrl()))) return true;
    }

    return false;
  }

  private static String url2path(String url) {
    String path = VfsUtil.urlToPath(url);

    int separatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return path;
    return path.substring(0, separatorIndex);
  }

  private int myInsideRefresh = 0;
  private boolean myPointerChangesDetected = false;

  private class AppListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      myInsideRefresh++;
    }

    public void writeActionFinished(Object action) {
      if (--myInsideRefresh == 0) {
        if (myPointerChangesDetected) {
          myPointerChangesDetected = false;
          myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).rootsChanged(new ModuleRootEventImpl(myProject, false));
  
          doSynchronizeRoots();

          addRootsToWatch();
        }
      }
    }
  }

  void addListenerForTable(LibraryTable.Listener libraryListener,
                           final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.addListener(libraryListener);
  }

  void removeListenerForTable(LibraryTable.Listener libraryListener,
                              final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.removeListener(libraryListener);
  }

  private final Map<LibraryTable, LibraryTableMultilistener> myLibraryTableMultilisteners
    = new HashMap<LibraryTable, LibraryTableMultilistener>();

  private class LibraryTableMultilistener implements LibraryTable.Listener {
    final Set<LibraryTable.Listener> myListeners = new HashSet<LibraryTable.Listener>();
    private final LibraryTable myLibraryTable;

    private LibraryTableMultilistener(LibraryTable libraryTable) {
      myLibraryTable = libraryTable;
      myLibraryTable.addListener(this);
      myLibraryTableMultilisteners.put(myLibraryTable, this);
    }

    private void addListener(LibraryTable.Listener listener) {
      myListeners.add(listener);
    }

    private void removeListener(LibraryTable.Listener listener) {
      myListeners.remove(listener);
      if (myListeners.isEmpty()) {
        myLibraryTable.removeListener(this);
        myLibraryTableMultilisteners.remove(myLibraryTable);
      }
    }

    public void afterLibraryAdded(final Library newLibrary) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryAdded(newLibrary);
          }
        }
      });
    }

    public void afterLibraryRenamed(final Library library) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRenamed(library);
          }
        }
      });
    }

    public void beforeLibraryRemoved(final Library library) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.beforeLibraryRemoved(library);
          }
        }
      });
    }

    public void afterLibraryRemoved(final Library library) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRemoved(library);
          }
        }
      });
    }
  }

  private JdkTableMultiListener myJdkTableMultiListener = null;

  private class JdkTableMultiListener implements ProjectJdkTable.Listener {
    final EventDispatcher<ProjectJdkTable.Listener> myDispatcher = EventDispatcher.create(ProjectJdkTable.Listener.class);
    final Set<ProjectJdkTable.Listener> myListeners = new HashSet<ProjectJdkTable.Listener>();
    private MessageBusConnection listenerConnection;

    private JdkTableMultiListener(Project project) {
      listenerConnection = project.getMessageBus().connect();
      listenerConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this);
    }

    private void addListener(ProjectJdkTable.Listener listener) {
      myDispatcher.addListener(listener);
      myListeners.add(listener);
    }

    private void removeListener(ProjectJdkTable.Listener listener) {
      myDispatcher.removeListener(listener);
      myListeners.remove(listener);
      uninstallListener(true);
    }

    public void jdkAdded(final Sdk jdk) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkAdded(jdk);
        }
      });
    }

    public void jdkRemoved(final Sdk jdk) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkRemoved(jdk);
        }
      });
    }

    public void jdkNameChanged(final Sdk jdk, final String previousName) {
      mergeRootsChangesDuring(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkNameChanged(jdk, previousName);
        }
      });
      String currentName = getProjectSdkName();
      if (previousName != null && previousName.equals(currentName)) {
        // if already had jdk name and that name was the name of the jdk just changed
        myProjectSdkName = jdk.getName();
        myProjectSdkType = jdk.getSdkType().getName();
      }
    }

    public void uninstallListener(boolean soft) {
      if (!soft || !myDispatcher.hasListeners()) {
        if (listenerConnection != null) {
          listenerConnection.disconnect();
          listenerConnection = null;
        }
      }
    }
  }

  private final Map<RootProvider, RootSetChangedMulticaster> myRegisteredRootProviderListeners = new HashMap<RootProvider, RootSetChangedMulticaster>();

  void addJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    getJdkTableMultiListener().addListener(jdkTableListener);
  }

  private JdkTableMultiListener getJdkTableMultiListener() {
    if (myJdkTableMultiListener == null) {
      myJdkTableMultiListener = new JdkTableMultiListener(myProject);
    }
    return myJdkTableMultiListener;
  }

  void removeJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    if (myJdkTableMultiListener == null) return;
    myJdkTableMultiListener.removeListener(jdkTableListener);
  }

  private class RootSetChangedMulticaster implements RootProvider.RootSetChangedListener {
    private final EventDispatcher<RootProvider.RootSetChangedListener> myDispatcher = EventDispatcher.create(RootProvider.RootSetChangedListener.class);
    private final RootProvider myProvider;

    private RootSetChangedMulticaster(RootProvider provider) {
      myProvider = provider;
      provider.addRootSetChangedListener(this);
      myRegisteredRootProviderListeners.put(myProvider, this);
    }

    private void addListener(RootProvider.RootSetChangedListener listener) {
      myDispatcher.addListener(listener);
    }

    private void removeListener(RootProvider.RootSetChangedListener listener) {
      myDispatcher.removeListener(listener);
      if (!myDispatcher.hasListeners()) {
        myProvider.removeRootSetChangedListener(this);
        myRegisteredRootProviderListeners.remove(myProvider);
      }
    }

    public void rootSetChanged(final RootProvider wrapper) {
      LOG.assertTrue(myProvider.equals(wrapper));
      Runnable runnable = new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().rootSetChanged(wrapper);
        }
      };
      mergeRootsChangesDuring(runnable);
    }
  }

  public long getModificationCount() {
    return myModificationCount;
  }
}
