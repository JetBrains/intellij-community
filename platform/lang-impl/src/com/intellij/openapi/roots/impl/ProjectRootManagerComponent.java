/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.BatchUpdateListener;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileBasedIndexProjectHandler;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * ProjectRootManager extended with ability to watch events.
 */
public class ProjectRootManagerComponent extends ProjectRootManagerImpl implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(ProjectRootManagerComponent.class);

  private boolean myPointerChangesDetected = false;
  private int myInsideRefresh = 0;
  private final BatchUpdateListener myHandler;
  private final MessageBusConnection myConnection;

  private Set<LocalFileSystem.WatchRequest> myRootsToWatch = new THashSet<>();
  private final boolean myDoLogCachesUpdate;

  public ProjectRootManagerComponent(Project project, StartupManager startupManager) {
    super(project);

    myConnection = project.getMessageBus().connect(project);
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void beforeFileTypesChanged(@NotNull FileTypeEvent event) {
        beforeRootsChange(true);
      }

      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        rootsChanged(true);
      }
    });

    VirtualFileManager.getInstance().addVirtualFileManagerListener(new VirtualFileManagerAdapter() {
      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        doUpdateOnRefresh();
      }
    }, project);

    startupManager.registerStartupActivity(() -> myStartupActivityPerformed = true);

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
    myDoLogCachesUpdate = ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ProjectRootManager";
  }

  @Override
  public void initComponent() {
    myConnection.subscribe(BatchUpdateListener.TOPIC, myHandler);
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
    if (myProject.isDefault()) {
      return;
    }

    if (myDoLogCachesUpdate) LOG.debug("refresh");
    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    DumbModeTask task = FileBasedIndexProjectHandler.createChangedFilesIndexingTask(myProject);
    if (task != null) {
      dumbService.queueTask(task);
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

    final Set<String> recursive = new HashSet<>();
    final Set<String> flat = new HashSet<>();

    final String projectFilePath = myProject.getProjectFilePath();
    final File projectDirFile = projectFilePath == null ? null : new File(projectFilePath).getParentFile();
    if (projectDirFile != null && projectDirFile.getName().equals(Project.DIRECTORY_STORE_FOLDER)) {
      recursive.add(projectDirFile.getAbsolutePath());
    }
    else {
      flat.add(projectFilePath);
      // may be not existing yet
      ContainerUtil.addIfNotNull(flat, ((IProjectStore)ServiceKt.getStateStore(myProject)).getWorkspaceFilePath());
    }

    for (WatchedRootsProvider extension : Extensions.getExtensions(WatchedRootsProvider.EP_NAME, myProject)) {
      recursive.addAll(extension.getRootsToWatch());
    }

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      flat.add(module.getModuleFilePath());

      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      addRootsToTrack(moduleRootManager.getContentRootUrls(), recursive, flat);

      if (includeSourceRoots) {
        addRootsToTrack(moduleRootManager.getSourceRootUrls(), recursive, flat);
      }

      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry libSdkEntry = (LibraryOrSdkOrderEntry)entry;
          for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
            addRootsToTrack(libSdkEntry.getRootUrls(orderRootType), recursive, flat);
          }
        }
      }
    }

    return Pair.create(recursive, flat);
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
        else if (StandardFileSystems.JRT_PROTOCOL.equals(protocol)) {
          recursive.add(extractLocalPath(url));
        }
      }
    }
  }

  @Override
  protected void doSynchronizeRoots() {
    if (!myStartupActivityPerformed) return;

    if (myDoLogCachesUpdate) LOG.debug(new Throwable("sync roots"));
    else if (!ApplicationManager.getApplication().isUnitTestMode()) LOG.info("project roots have changed");

    DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);
    if (FileBasedIndex.getInstance() instanceof FileBasedIndexImpl) {
      dumbService.queueTask(new UnindexedFilesUpdater(myProject, false));
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
    public void beforeWriteActionStart(@NotNull Object action) {
      myInsideRefresh++;
    }

    @Override
    public void writeActionFinished(@NotNull Object action) {
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
            if (myDoLogCachesUpdate) LOG.debug(new Throwable(pointers.length > 0 ? pointers[0].getPresentableUrl():""));
          }
        }
        else if (!myPointerChangesDetected) {
          //this is the first pointer changing validity
          if (affectsRoots(pointers)) {
            myPointerChangesDetected = true;
            myProject.getMessageBus().syncPublisher(ProjectTopics.PROJECT_ROOTS).beforeRootsChange(new ModuleRootEventImpl(myProject, false));
            if (myDoLogCachesUpdate) LOG.debug(new Throwable(pointers.length > 0 ? pointers[0].getPresentableUrl():""));
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
