/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Query;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;

public class DirectoryIndexImpl extends DirectoryIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  private final Project myProject;
  private final MessageBusConnection myConnection;

  private volatile boolean myDisposed = false;
  private volatile RootIndex myRootIndex = null;

  public DirectoryIndexImpl(@NotNull Project project) {
    myProject = project;
    myConnection = project.getMessageBus().connect(project);
    subscribeToFileChanges();
    markContentRootsForRefresh();
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myDisposed = true;
        myRootIndex = null;
      }
    });
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener.Adapter() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        myRootIndex = null;
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        myRootIndex = null;
      }
    });

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        RootIndex rootIndex = myRootIndex;
        if (rootIndex != null && rootIndex.resetOnEvents(events)) {
          myRootIndex = null;
        }
      }
    });
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

  @Override
  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getRootIndex().getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @NotNull
  private RootIndex getRootIndex() {
    RootIndex rootIndex = myRootIndex;
    if (rootIndex == null) {
      myRootIndex = rootIndex = new RootIndex(myProject, new RootIndex.InfoCache() {
        // Upsource can't use int-mapping because different files may have the same id there
        private final ConcurrentIntObjectMap<DirectoryInfo> myInfoCache = new StripedLockIntObjectConcurrentHashMap<DirectoryInfo>();
        @Override
        public void cacheInfo(@NotNull VirtualFile dir, @NotNull DirectoryInfo info) {
          myInfoCache.put(((NewVirtualFile)dir).getId(), info);
        }

        @Override
        public DirectoryInfo getCachedInfo(@NotNull VirtualFile dir) {
          return myInfoCache.get(((NewVirtualFile)dir).getId());
        }
      });
    }
    return rootIndex;
  }

  @Override
  @TestOnly
  public void checkConsistency() {
    getRootIndex().checkConsistency();
  }

  @Override
  public DirectoryInfo getInfoForDirectory(@NotNull VirtualFile dir) {
    DirectoryInfo info = getInfoForFile(dir);
    return info.isInProject() ? info : null;
  }

  @NotNull
  @Override
  public DirectoryInfo getInfoForFile(@NotNull VirtualFile file) {
    checkAvailability();
    dispatchPendingEvents();

    if (!(file instanceof NewVirtualFile)) return NonProjectDirectoryInfo.NOT_SUPPORTED_VIRTUAL_FILE_IMPLEMENTATION;

    return getRootIndex().getInfoForFile(file);
  }

  @Override
  @Nullable
  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo info) {
    if (info.isInModuleSource()) {
      return getRootIndex().getSourceRootType(info);
    }
    return null;
  }

  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    checkAvailability();
    if (!(dir instanceof NewVirtualFile)) return null;

    return getRootIndex().getPackageName(dir);
  }

  private void checkAvailability() {
    if (myDisposed) {
      ProgressManager.checkCanceled();
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }

}
