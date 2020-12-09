// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ProjectComponent;
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
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
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
import com.intellij.ui.GuiUtils;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileBasedIndexProjectHandler;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * ProjectRootManager extended with ability to watch events.
 */
public class ProjectRootManagerComponent extends ProjectRootManagerImpl implements ProjectComponent, Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectRootManagerComponent.class);
  private static final boolean LOG_CACHES_UPDATE =
    ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode();

  private final static ExtensionPointName<WatchedRootsProvider> WATCHED_ROOTS_PROVIDER_EP_NAME = new ExtensionPointName<>("com.intellij.roots.watchedRootsProvider");

  private final ExecutorService myExecutor = ApplicationManager.getApplication().isUnitTestMode()
                                             ? ConcurrencyUtil.newSameThreadExecutorService()
                                             : AppExecutorUtil.createBoundedApplicationPoolExecutor("Project Root Manager", 1);
  private @NotNull Future<?> myCollectWatchRootsFuture = CompletableFuture.completedFuture(null); // accessed in EDT only

  private final OnlyOnceExceptionLogger myRootsChangedLogger = new OnlyOnceExceptionLogger(LOG);

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

  private void registerListeners() {
    MessageBusConnection connection = myProject.getMessageBus().connect(this);
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

    if (!LightEdit.owns(myProject)) {
      VirtualFileManager.getInstance().addVirtualFileManagerListener(new VirtualFileManagerListener() {
        @Override
        public void afterRefreshFinish(boolean asynchronous) {
          doUpdateOnRefresh();
        }
      }, this);
    }
    StartupManager.getInstance(myProject).registerStartupActivity(() -> {
      myStartupActivityPerformed = true;
    });

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
    Runnable rootsExtensionPointListener = () -> ApplicationManager.getApplication().invokeLater(() -> {
      WriteAction.run(() -> {
        makeRootsChange(EmptyRunnable.getInstance(), false, true);
      });
    });
    AdditionalLibraryRootsProvider.EP_NAME.addChangeListener(rootsExtensionPointListener, this);
    OrderEnumerationHandler.EP_NAME.addChangeListener(rootsExtensionPointListener, this);
  }

  @Override
  public void projectOpened() {
    addRootsToWatch();
    ApplicationManager.getApplication().addApplicationListener(new AppListener(), myProject);
  }

  @Override
  public void projectClosed() {
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
      GuiUtils.invokeLaterIfNeeded(() -> {
        if (myProject.isDisposed()) return;
        myRootPointersDisposable = newDisposable;
        // dispose after the re-creating container to keep VFPs from disposing and re-creating back;
        // instead, just increment/decrement their usage count
        Disposer.dispose(oldDisposable);
        myRootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(myRootsToWatch, watchRoots.first, watchRoots.second);
      }, ModalityState.any());
    });
  }

  private void doUpdateOnRefresh() {
    if (ApplicationManager.getApplication().isUnitTestMode() && (!myStartupActivityPerformed || myProject.isDisposed())) {
      return; // in test mode suppress addition to a queue unless project is properly initialized
    }

    if (LOG_CACHES_UPDATE || LOG.isDebugEnabled()) {
      LOG.debug("refresh");
    }
    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    DumbModeTask task = FileBasedIndexProjectHandler.createChangedFilesIndexingTask(myProject);
    if (task != null) {
      dumbService.queueTask(task);
    }
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
  protected void fireRootsChangedEvent(boolean fileTypes, @Nullable ProjectRootManagerImpl.RootsChangeType changeType) {
    isFiringEvent = true;
    try {
      myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).rootsChanged(new ModuleRootEventImpl(myProject, fileTypes));
    }
    finally {
      isFiringEvent = false;
    }

    synchronizeRoots(changeType);
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
    if (!flatPaths.isEmpty() || !excludedUrls.isEmpty()) {
      Disposer.register(this, disposable);
      // creating a container with these URLs with the sole purpose to get events to getRootsValidityChangedListener() when these roots change
      VirtualFilePointerContainer container =
        VirtualFilePointerManager.getInstance().createContainer(disposable, getRootsValidityChangedListener());

      flatPaths.forEach(path -> container.add(VfsUtilCore.pathToUrl(path)));
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

  private void synchronizeRoots(@Nullable ProjectRootManagerImpl.RootsChangeType changeType) {
    if (!myStartupActivityPerformed) return;

    if (changeType == RootsChangeType.ROOTS_REMOVED) {
      LOG.info("some project roots were removed");
      return;
    }

    String message = "project roots have changed";
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info(message);
    }
    else {
      myRootsChangedLogger.info(message, new Throwable());
    }

    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    if (FileBasedIndex.getInstance() instanceof FileBasedIndexImpl) {
      dumbService.queueTask(new UnindexedFilesUpdater(myProject));
    }
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
    private ProjectRootManagerImpl.RootsChangeType getPointersChanges(VirtualFilePointer @NotNull [] pointers) {
      RootsChangeType result = null;
      for (VirtualFilePointer pointer : pointers) {
        if (pointer.isValid()) {
          if (result == null) {
            result = RootsChangeType.ROOTS_ADDED;
          }
          else if (result != RootsChangeType.ROOTS_ADDED) {
            return RootsChangeType.GENERIC;
          }
        }
        else {
          if (result == null) {
            result = RootsChangeType.ROOTS_REMOVED;
          }
          else if (result != RootsChangeType.ROOTS_REMOVED) {
            return RootsChangeType.GENERIC;
          }
        }
      }
      return ObjectUtils.notNull(result, RootsChangeType.GENERIC);
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
      RootsChangeType changeType = getPointersChanges(pointers);

      if (myProject.isDisposed()) {
        return;
      }

      if (isInsideWriteAction()) {
        myRootsChanged.rootsChanged(changeType);
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