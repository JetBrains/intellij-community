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

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.file.exclude.ProjectFileExclusionManagerImpl;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class FileBasedIndexProjectHandler extends AbstractProjectComponent implements IndexableFileSet {
  private final FileBasedIndexImpl myIndex;
  private final ProjectRootManagerEx myRootManager;
  private final FileTypeManager myFileTypeManager;
  private final ProjectFileExclusionManagerImpl myExclusionManager;

  public FileBasedIndexProjectHandler(final FileBasedIndexImpl index, final Project project, final ProjectRootManagerComponent rootManager, FileTypeManager ftManager, final ProjectManager projectManager) {
    super(project);
    myIndex = index;
    myRootManager = rootManager;
    myFileTypeManager = ftManager;
    myExclusionManager = ProjectFileExclusionManagerImpl.getInstance(project);

    final StartupManagerEx startupManager = (StartupManagerEx)StartupManager.getInstance(project);
    if (startupManager != null) {
      startupManager.registerPreStartupActivity(new Runnable() {
        @Override
        public void run() {
          final RefreshCacheUpdater changedFilesUpdater = new RefreshCacheUpdater();
          final UnindexedFilesUpdater unindexedFilesUpdater = new UnindexedFilesUpdater(project, index);

          startupManager.registerCacheUpdater(unindexedFilesUpdater);
          rootManager.registerRootsChangeUpdater(unindexedFilesUpdater);
          rootManager.registerRefreshUpdater(changedFilesUpdater);
          myIndex.registerIndexableSet(FileBasedIndexProjectHandler.this, project);
          projectManager.addProjectManagerListener(project, new ProjectManagerAdapter() {
            private boolean removed = false;
            @Override
            public void projectClosing(Project project) {
              if (!removed) {
                removed = true;
                rootManager.unregisterRefreshUpdater(changedFilesUpdater);
                rootManager.unregisterRootsChangeUpdater(unindexedFilesUpdater);
                myIndex.removeIndexableSet(FileBasedIndexProjectHandler.this);
              }
            }
          });
        }
      });
    }
  }

  @Override
  public boolean isInSet(@NotNull final VirtualFile file) {
    final ProjectFileIndex index = myRootManager.getFileIndex();
    if (index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file)) {
      if (myExclusionManager != null && myExclusionManager.isExcluded(file)) return false;
      return !myFileTypeManager.isFileIgnored(file);
    }
    return false;
  }

  @Override
  public void iterateIndexableFilesIn(@NotNull final VirtualFile file, @NotNull final ContentIterator iterator) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {

        if (!isInSet(file)) return false;
        iterator.processFile(file);

        return true;
      }
    });
  }

  @Override
  public void disposeComponent() {
    // done mostly for tests. In real life this is noop, because the set was removed on project closing
    myIndex.removeIndexableSet(this);
  }

  private class RefreshCacheUpdater implements CacheUpdater {
    @Override
    public int getNumberOfPendingUpdateJobs() {
      return myIndex.getNumberOfPendingInvalidations();
    }

    @Override
    public VirtualFile[] queryNeededFiles(ProgressIndicator indicator) {
      Collection<VirtualFile> files = myIndex.getFilesToUpdate(myProject);
      return VfsUtilCore.toVirtualFileArray(files);
    }

    @Override
    public void processFile(FileContent fileContent) {
      myIndex.processRefreshedFile(myProject, fileContent);
    }

    @Override
    public void updatingDone() {
    }

    @Override
    public void canceled() {
    }
  }
}
