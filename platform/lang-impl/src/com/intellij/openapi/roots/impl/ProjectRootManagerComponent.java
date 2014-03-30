/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.stores.BatchUpdateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * ProjectRootManager extended with ability to watch events.
 */
public class ProjectRootManagerComponent extends ProjectRootManagerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectManagerComponent");
  private static final boolean ourScheduleCacheUpdateInDumbMode = SystemProperties.getBooleanProperty(
    "idea.schedule.cache.update.in.dumb.mode", true);
  private boolean myPointerChangesDetected = false;
  private int myInsideRefresh = 0;
  private final BatchUpdateListener myHandler;
  private final MessageBusConnection myConnection;

  protected final List<CacheUpdater> myRootsChangeUpdaters = new ArrayList<CacheUpdater>();
  protected final List<CacheUpdater> myRefreshCacheUpdaters = new ArrayList<CacheUpdater>();

  private Set<LocalFileSystem.WatchRequest> myRootsToWatch = new HashSet<LocalFileSystem.WatchRequest>();

  public ProjectRootManagerComponent(Project project,
                                     DirectoryIndex directoryIndex,
                                     StartupManager startupManager) {
    super(project, directoryIndex);

    myConnection = project.getMessageBus().connect(project);
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void beforeFileTypesChanged(FileTypeEvent event) {
        beforeRootsChange(true);
      }

      @Override
      public void fileTypesChanged(FileTypeEvent event) {
        rootsChanged(true);
      }
    });

    VirtualFileManager.getInstance().addVirtualFileManagerListener(new VirtualFileManagerAdapter() {
      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        doUpdateOnRefresh();
      }
    }, project);

    startupManager.registerStartupActivity(new Runnable() {
      @Override
      public void run() {
        myStartupActivityPerformed = true;
      }
    });

    myHandler = new BatchUpdateListener() {
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

  public void registerRefreshUpdater(CacheUpdater updater) {
    myRefreshCacheUpdaters.add(updater);
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
    boolean removed = myRefreshCacheUpdaters.remove(updater);
    LOG.assertTrue(removed);
  }

  @Override
  public void initComponent() {
    super.initComponent();
    myConnection.subscribe(BatchUpdateListener.TOPIC, myHandler);
  }

  @Override
  public void projectOpened() {
    super.projectOpened();
    addRootsToWatch();
    AppListener applicationListener = new AppListener();
    ApplicationManager.getApplication().addApplicationListener(applicationListener, myProject);
  }

  @Override
  public void projectClosed() {
    super.projectClosed();
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
  }

  @Override
  protected void addRootsToWatch() {
    final Pair<Set<String>, Set<String>> roots = getAllRoots(false);
    if (roots == null) return;
    myRootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(myRootsToWatch, roots.first, roots.second);
  }

  private void beforeRootsChange(boolean fileTypes) {
    if (myProject.isDisposed()) return;
    getBatchSession(fileTypes).beforeRootsChanged();
  }

  private void rootsChanged(boolean fileTypes) {
    getBatchSession(fileTypes).rootsChanged();
  }

  private void doUpdateOnRefresh() {
    if (ApplicationManager.getApplication().isUnitTestMode() && (!myStartupActivityPerformed || myProject.isDisposed())) {
      return; // in test mode suppress addition to a queue unless project is properly initialized
    }
    if (myRefreshCacheUpdaters.size() == 0) {
      return; // default project
    }
    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    if (ourScheduleCacheUpdateInDumbMode) {
      dumbService.queueCacheUpdateInDumbMode(myRefreshCacheUpdaters);
    }
    else {
      dumbService.queueCacheUpdate(myRefreshCacheUpdaters);
    }
  }

  private boolean affectsRoots(VirtualFilePointer[] pointers) {
    Pair<Set<String>, Set<String>> roots = getAllRoots(true);
    if (roots == null) return false;

    for (VirtualFilePointer pointer : pointers) {
      final String path = url2path(pointer.getUrl());
      if (roots.first.contains(path) || roots.second.contains(path)) return true;
    }

    return false;
  }

  @Override
  protected void fireBeforeRootsChangeEvent(boolean fileTypes) {
    isFiringEvent = true;
    try {
      myProject.getMessageBus()
        .syncPublisher(ProjectTopics.PROJECT_ROOTS)
        .beforeRootsChange(new ModuleRootEventImpl(myProject, fileTypes));
    }
    finally {
      isFiringEvent= false;
    }
  }

  @Override
  protected void fireRootsChangedEvent(boolean fileTypes) {
    isFiringEvent = true;
    try {
      myProject.getMessageBus()
        .syncPublisher(ProjectTopics.PROJECT_ROOTS)
        .rootsChanged(new ModuleRootEventImpl(myProject, fileTypes));
    }
    finally {
      isFiringEvent = false;
    }
  }

  private static String url2path(String url) {
    String path = VfsUtilCore.urlToPath(url);

    int separatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return path;
    return path.substring(0, separatorIndex);
  }

  @Nullable
  private Pair<Set<String>, Set<String>> getAllRoots(boolean includeSourceRoots) {
    if (myProject.isDefault()) return null;

    final Set<String> recursive = new HashSet<String>();
    final Set<String> flat = new HashSet<String>();

    final String projectFilePath = myProject.getProjectFilePath();
    final File projectDirFile = new File(projectFilePath).getParentFile();
    if (projectDirFile != null && projectDirFile.getName().equals(Project.DIRECTORY_STORE_FOLDER)) {
      recursive.add(projectDirFile.getAbsolutePath());
    }
    else {
      flat.add(projectFilePath);
      final VirtualFile workspaceFile = myProject.getWorkspaceFile();
      if (workspaceFile != null) {
        flat.add(workspaceFile.getPath());
      }
    }

    for (WatchedRootsProvider extension : Extensions.getExtensions(WatchedRootsProvider.EP_NAME, myProject)) {
      recursive.addAll(extension.getRootsToWatch());
    }

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      addRootsToTrack(moduleRootManager.getContentRootUrls(), recursive, flat);
      if (includeSourceRoots) {
        addRootsToTrack(moduleRootManager.getSourceRootUrls(), recursive, flat);
      }
      flat.add(module.getModuleFilePath());

      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
              addRootsToTrack(library.getUrls(orderRootType), recursive, flat);
            }
          }
        }
        else if (entry instanceof JdkOrderEntry) {
          for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
            addRootsToTrack(((JdkOrderEntry)entry).getRootUrls(orderRootType), recursive, flat);
          }
        }
      }
    }

    return Pair.create(recursive, flat);
  }

  @Override
  protected void doSynchronizeRoots() {
    if (!myStartupActivityPerformed) return;

    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    if (ourScheduleCacheUpdateInDumbMode) {
      dumbService.queueCacheUpdateInDumbMode(myRootsChangeUpdaters);
    } else {
      dumbService.queueCacheUpdate(myRootsChangeUpdaters);
    }
  }

  private static void addRootsToTrack(final String[] urls, final Collection<String> recursive, final Collection<String> flat) {
    for (String url : urls) {
      if (url != null) {
        final String protocol = VirtualFileManager.extractProtocol(url);
        if (protocol == null || LocalFileSystem.PROTOCOL.equals(protocol)) {
          recursive.add(extractLocalPath(url));
        }
        else if (JarFileSystem.PROTOCOL.equals(protocol)) {
          flat.add(extractLocalPath(url));
        }
      }
    }
  }

  @Override
  protected void clearScopesCaches() {
    super.clearScopesCaches();
    LibraryScopeCache.getInstance(myProject).clear();
  }

  @Override
  public void clearScopesCachesForModules() {
    super.clearScopesCachesForModules();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleEx)module).clearScopesCache();
    }
  }

  private class AppListener extends ApplicationAdapter {
    @Override
    public void beforeWriteActionStart(Object action) {
      myInsideRefresh++;
    }

    @Override
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

  private class MyVirtualFilePointerListener implements VirtualFilePointerListener {
    @Override
    public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
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

    @Override
    public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
      if (!myProject.isDisposed()) {
        if (myInsideRefresh > 0) {
          clearScopesCaches();
        }
        else if (affectsRoots(pointers)) {
          rootsChanged(false);
        }
      }
    }
  }
}
