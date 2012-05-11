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
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * ProjectRootManager extended with ability to watch events.
 */
public class ProjectRootManagerComponent extends ProjectRootManagerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectManagerComponent");

  private boolean myPointerChangesDetected = false;
  private int myInsideRefresh = 0;
  private final BatchUpdateListener myHandler;
  private final MessageBusConnection myConnection;

  protected final List<CacheUpdater> myRootsChangeUpdaters = new ArrayList<CacheUpdater>();
  protected final List<CacheUpdater> myRefreshCacheUpdaters = new ArrayList<CacheUpdater>();

  private Set<LocalFileSystem.WatchRequest> myRootsToWatch = new HashSet<LocalFileSystem.WatchRequest>();

  public ProjectRootManagerComponent(Project project,
                                     FileTypeManager fileTypeManager,
                                     DirectoryIndex directoryIndex,
                                     StartupManager startupManager) {
    super(project, directoryIndex);

    myConnection = project.getMessageBus().connect(project);
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
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
    }, project);

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

  protected void addRootsToWatch() {
    final Set<String> rootPaths = getAllRoots(false);
    if (rootPaths == null) return;
    myRootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(myRootsToWatch, rootPaths, true);
  }

  private void beforeRootsChange(boolean filetypes) {
    if (myProject.isDisposed()) return;
    getBatchSession(filetypes).beforeRootsChanged();
  }

  private void rootsChanged(boolean filetypes) {
    getBatchSession(filetypes).rootsChanged();
  }

  private void doUpdateOnRefresh() {
    if (ApplicationManager.getApplication().isUnitTestMode() && (!myStartupActivityPerformed || myProject.isDisposed())) {
      return; // in test mode suppress addition to a queue unless project is properly initialized
    }
    DumbServiceImpl.getInstance(myProject).queueCacheUpdate(myRefreshCacheUpdaters);
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
    String path = VfsUtilCore.urlToPath(url);

    int separatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return path;
    return path.substring(0, separatorIndex);
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

    final String projectFile = myProject.getProjectFilePath();
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

  @Override
  protected void doSynchronizeRoots() {
    if (!myStartupActivityPerformed) return;
    DumbServiceImpl.getInstance(myProject).queueCacheUpdate(myRootsChangeUpdaters);
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

  private class MyVirtualFilePointerListener implements VirtualFilePointerListener {
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
