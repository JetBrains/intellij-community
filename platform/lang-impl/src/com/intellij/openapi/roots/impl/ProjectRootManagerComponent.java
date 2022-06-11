// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.impl.stores.BatchUpdateListener;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.project.ProjectKt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.roots.AdditionalLibraryRootsContributor;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * ProjectRootManager extended with ability to watch events.
 */
public class ProjectRootManagerComponent extends ProjectRootManagerImpl implements Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectRootManagerComponent.class);
  private static final boolean LOG_CACHES_UPDATE =
    ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode();

  private final static ExtensionPointName<WatchedRootsProvider> WATCHED_ROOTS_PROVIDER_EP_NAME = new ExtensionPointName<>("com.intellij.roots.watchedRootsProvider");

  private final ExecutorService myExecutor = ApplicationManager.getApplication().isUnitTestMode()
                                             ? ConcurrencyUtil.newSameThreadExecutorService()
                                             : AppExecutorUtil.createBoundedApplicationPoolExecutor("Project Root Manager", 1);
  private @NotNull Future<?> myCollectWatchRootsFuture = CompletableFuture.completedFuture(null); // accessed in EDT only

  private boolean myPointerChangesDetected;
  private int myInsideWriteAction;
  private @NotNull Set<LocalFileSystem.WatchRequest> myRootsToWatch = CollectionFactory.createSmallMemoryFootprintSet();
  private Disposable myRootPointersDisposable = Disposer.newDisposable(); // accessed in EDT

  public ProjectRootManagerComponent(@NotNull Project project) {
    super(project);

    if (!myProject.isDefault()) {
      registerListeners();
    }
  }

  @NotNull Set<LocalFileSystem.WatchRequest> getRootsToWatch() {
    return myRootsToWatch;
  }

  private void registerListeners() {
    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        if (project == myProject) {
          addRootsToWatch();
          ApplicationManager.getApplication().addApplicationListener(new AppListener(), myProject);
        }
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        if (project == myProject) {
          ProjectRootManagerComponent.this.projectClosed();
        }
      }
    });
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void beforeFileTypesChanged(@NotNull FileTypeEvent event) {
        myFileTypesChanged.beforeRootsChanged();
      }

      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        myFileTypesChanged.rootsChanged();
      }
    });

    StartupManager.getInstance(myProject).registerStartupActivity(() -> myStartupActivityPerformed = true);

    connection.subscribe(BatchUpdateListener.TOPIC, new BatchUpdateListener() {
      @Override
      public void onBatchUpdateStarted() {
        myRootsChanged.levelUp();
        myFileTypesChanged.levelUp();
      }

      @Override
      public void onBatchUpdateFinished() {
        myRootsChanged.levelDown();
        myFileTypesChanged.levelDown();
      }
    });
    Runnable rootsExtensionPointListener = () -> ApplicationManager.getApplication().invokeLater(() ->
      WriteAction.run(() -> makeRootsChange(EmptyRunnable.getInstance(), false, true))
    );
    AdditionalLibraryRootsProvider.EP_NAME.addChangeListener(rootsExtensionPointListener, this);
    OrderEnumerationHandler.EP_NAME.addChangeListener(rootsExtensionPointListener, this);


    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, (presentableLibraryName, oldRoots, newRoots, libraryNameForDebug) -> {
      if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) {
        return;
      }
      List<VirtualFile> rootsToIndex = new ArrayList<>(newRoots.size());
      for (VirtualFile root : newRoots) {
        boolean shouldIndex = true;
        for (VirtualFile oldRoot : oldRoots) {
          if (VfsUtilCore.isAncestor(oldRoot, root, false)) {
            shouldIndex = false;
            break;
          }
        }
        if (shouldIndex) {
          rootsToIndex.add(root);
        }
      }

      if (rootsToIndex.isEmpty()) return;

      List<IndexableFilesIterator> indexableFilesIterators =
        Collections.singletonList(AdditionalLibraryRootsContributor.createIndexingIterator(presentableLibraryName, rootsToIndex, libraryNameForDebug));

      new UnindexedFilesUpdater(myProject, indexableFilesIterators, "On updated roots of library '" + presentableLibraryName + "'").queue(myProject);
    });
  }

  protected void projectClosed() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
  }

  @RequiresEdt
  private void addRootsToWatch() {
    if (myProject.isDefault()) return;
    ApplicationManager.getApplication().assertIsWriteThread();
    Disposable oldDisposable = myRootPointersDisposable;
    Disposable newDisposable = Disposer.newDisposable();

    myCollectWatchRootsFuture.cancel(false);
    myCollectWatchRootsFuture = myExecutor.submit(() -> {
      Pair<Set<String>, Set<String>> watchRoots = ReadAction.compute(() -> myProject.isDisposed() ? null : collectWatchRoots(newDisposable));
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
        if (myProject.isDisposed()) return;
        myRootPointersDisposable = newDisposable;
        // dispose after the re-creating container to keep VFPs from disposing and re-creating back;
        // instead, just increment/decrement their usage count
        Disposer.dispose(oldDisposable);
        myRootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(myRootsToWatch, watchRoots.first, watchRoots.second);
      });
    });
  }

  @Override
  protected void fireBeforeRootsChangeEvent(boolean fileTypes) {
    isFiringEvent = true;
    try {
      myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).beforeRootsChange(new ModuleRootEventImpl(myProject, fileTypes));
    }
    finally {
      isFiringEvent = false;
    }
  }

  @Override
  protected void fireRootsChangedEvent(boolean fileTypes, @NotNull List<? extends RootsChangeIndexingInfo> indexingInfos) {
    isFiringEvent = true;
    try {
      myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).rootsChanged(new ModuleRootEventImpl(myProject, fileTypes, indexingInfos));
    }
    finally {
      isFiringEvent = false;
    }

    synchronizeRoots(indexingInfos);
    addRootsToWatch();
  }

  private @NotNull Pair<Set<String>, Set<String>> collectWatchRoots(@NotNull Disposable disposable) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Set<String> recursivePathsToWatch = CollectionFactory.createFilePathSet();
    Set<String> flatPaths = CollectionFactory.createFilePathSet();

    IProjectStore store = ProjectKt.getStateStore(myProject);
    Path projectFilePath = store.getProjectFilePath();
    if (!Project.DIRECTORY_STORE_FOLDER.equals(projectFilePath.getParent().getFileName().toString())) {
      flatPaths.add(FileUtil.toSystemIndependentName(projectFilePath.toString()));
      flatPaths.add(FileUtil.toSystemIndependentName(store.getWorkspacePath().toString()));
    }

    for (AdditionalLibraryRootsProvider extension : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      Collection<VirtualFile> toWatch = extension.getRootsToWatch(myProject);
      if (!toWatch.isEmpty()) {
        for (VirtualFile file : toWatch) {
          recursivePathsToWatch.add(file.getPath());
        }
      }
    }

    for (WatchedRootsProvider extension : WATCHED_ROOTS_PROVIDER_EP_NAME.getExtensionList()) {
      Set<String> toWatch = extension.getRootsToWatch(myProject);
      if (!toWatch.isEmpty()) {
        for (String path : toWatch) {
          recursivePathsToWatch.add(FileUtil.toSystemIndependentName(path));
        }
      }
    }

    Set<String> excludedUrls = CollectionFactory.createSmallMemoryFootprintSet();
    // changes in files provided by this method should be watched manually because no-one's bothered to set up correct pointers for them
    for (DirectoryIndexExcludePolicy excludePolicy : DirectoryIndexExcludePolicy.EP_NAME.getExtensionList(myProject)) {
      Collections.addAll(excludedUrls, excludePolicy.getExcludeUrlsForProject());
    }

    // avoid creating empty unnecessary container
    if (!excludedUrls.isEmpty()) {
      Disposer.register(this, disposable);
      // creating a container with these URLs with the sole purpose to get events to getRootsValidityChangedListener() when these roots change
      VirtualFilePointerContainer container =
        VirtualFilePointerManager.getInstance().createContainer(disposable, getRootsValidityChangedListener());
      ((VirtualFilePointerContainerImpl)container).addAll(excludedUrls);
    }

    // module roots already fire validity change events, see usages of ProjectRootManagerComponent.getRootsValidityChangedListener
    collectModuleWatchRoots(recursivePathsToWatch, flatPaths);
    return new Pair<>(recursivePathsToWatch, flatPaths);
  }

  private void collectModuleWatchRoots(@NotNull Set<? super String> recursivePaths, @NotNull Set<? super String> flatPaths) {
    Set<String> urls = CollectionFactory.createFilePathSet();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      ContainerUtil.addAll(urls, rootManager.getContentRootUrls());

      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach(entry -> {
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          ContainerUtil.addAll(urls, entry.getUrls(type));
        }
        return true;
      });
    }

    for (String url : urls) {
      String protocol = VirtualFileManager.extractProtocol(url);
      if (protocol == null || StandardFileSystems.FILE_PROTOCOL.equals(protocol)) {
        recursivePaths.add(extractLocalPath(url));
      }
      else if (StandardFileSystems.JAR_PROTOCOL.equals(protocol)) {
        flatPaths.add(extractLocalPath(url));
      }
      else if (StandardFileSystems.JRT_PROTOCOL.equals(protocol)) {
        recursivePaths.add(extractLocalPath(url));
      }
    }
  }

  private void synchronizeRoots(@NotNull List<? extends RootsChangeIndexingInfo> indexingInfos) {
    if (!myStartupActivityPerformed) return;
    EntityIndexingService.getInstance().indexChanges(myProject, indexingInfos);
  }

  @Override
  protected void clearScopesCaches() {
    super.clearScopesCaches();

    LibraryScopeCache libraryScopeCache = myProject.getServiceIfCreated(LibraryScopeCache.class);
    if (libraryScopeCache != null) {
      libraryScopeCache.clear();
    }
  }

  @Override
  public void clearScopesCachesForModules() {
    super.clearScopesCachesForModules();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleEx)module).clearScopesCache();
    }
  }

  @Override
  public void markRootsForRefresh() {
    Set<String> paths = CollectionFactory.createFilePathSet();
    collectModuleWatchRoots(paths, paths);

    LocalFileSystem fs = LocalFileSystem.getInstance();
    for (String path : paths) {
      VirtualFile root = fs.findFileByPath(path);
      if (root instanceof NewVirtualFile) {
        ((NewVirtualFile)root).markDirtyRecursively();
      }
    }
  }

  @Override
  public void dispose() {
    myCollectWatchRootsFuture.cancel(false);
    myExecutor.shutdownNow();
  }

  private class AppListener implements ApplicationListener {
    @Override
    public void beforeWriteActionStart(@NotNull Object action) {
      myInsideWriteAction++;
    }

    @Override
    public void writeActionFinished(@NotNull Object action) {
      if (--myInsideWriteAction == 0 && myPointerChangesDetected) {
        myPointerChangesDetected = false;
        myRootsChanged.levelDown();
      }
    }
  }

  private final VirtualFilePointerListener myRootsChangedListener = new VirtualFilePointerListener() {
    @NotNull
    private RootsChangeIndexingInfo getPointersChanges(VirtualFilePointer @NotNull [] pointers) {
      RootsChangeIndexingInfo result = null;
      for (VirtualFilePointer pointer : pointers) {
        if (pointer.isValid()) {
          return RootsChangeIndexingInfo.TOTAL_REINDEX;
        }
        else {
          if (result == null) {
            result = RootsChangeIndexingInfo.NO_INDEXING_NEEDED;
          }
        }
      }
      return ObjectUtils.notNull(result, RootsChangeIndexingInfo.TOTAL_REINDEX);
    }

    @Override
    public void beforeValidityChanged(VirtualFilePointer @NotNull [] pointers) {
      if (myProject.isDisposed()) {
        return;
      }

      if (!isInsideWriteAction() && !myPointerChangesDetected) {
        myPointerChangesDetected = true;
        //this is the first pointer changing validity
        myRootsChanged.levelUp();
      }

      myRootsChanged.beforeRootsChanged();
      if (LOG_CACHES_UPDATE || LOG.isTraceEnabled()) {
        LOG.trace(new Throwable(pointers.length > 0 ? pointers[0].getPresentableUrl() : ""));
      }
    }

    @Override
    public void validityChanged(VirtualFilePointer @NotNull [] pointers) {
      RootsChangeIndexingInfo changeInfo = getPointersChanges(pointers);

      if (myProject.isDisposed()) {
        return;
      }

      if (isInsideWriteAction()) {
        myRootsChanged.rootsChanged(changeInfo);
      }
      else {
        clearScopesCaches();
      }
    }

    private boolean isInsideWriteAction() {
      return myInsideWriteAction == 0;
    }
  };

  @Override
  public @NotNull VirtualFilePointerListener getRootsValidityChangedListener() {
    return myRootsChangedListener;
  }
}
