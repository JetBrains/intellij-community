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

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

public class FileBasedIndexProjectHandler extends AbstractProjectComponent implements IndexableFileSet {
  private final FileBasedIndex myIndex;
  private final ProjectRootManagerEx myRootManager;
  private final FileTypeManager myFileTypeManager;

  public FileBasedIndexProjectHandler(final FileBasedIndex index, final Project project, final ProjectRootManagerEx rootManager, FileTypeManager ftManager, final ProjectManager projectManager) {
    super(project);
    myIndex = index;
    myRootManager = rootManager;
    myFileTypeManager = ftManager;

    final StartupManagerEx startupManager = (StartupManagerEx)StartupManager.getInstance(project);
    if (startupManager != null) {
      startupManager.registerPreStartupActivity(new Runnable() {
        public void run() {
          final RefreshCacheUpdater refreshUpdater = new RefreshCacheUpdater();
          final UnindexedFilesUpdater rootsChangeUpdater = new UnindexedFilesUpdater(project, index);

          startupManager.registerCacheUpdater(rootsChangeUpdater);
          rootManager.registerRootsChangeUpdater(rootsChangeUpdater);
          rootManager.registerRefreshUpdater(refreshUpdater);
          myIndex.registerIndexableSet(FileBasedIndexProjectHandler.this, project);
          projectManager.addProjectManagerListener(project, new ProjectManagerAdapter() {
            public void projectClosing(Project project) {
              rootManager.unregisterRefreshUpdater(refreshUpdater);
              rootManager.unregisterRootsChangeUpdater(rootsChangeUpdater);
              myIndex.removeIndexableSet(FileBasedIndexProjectHandler.this);
            }
          });
        }
      });
    }
  }

  public boolean isInSet(final VirtualFile file) {
    final ProjectFileIndex index = myRootManager.getFileIndex();
    if (index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file)) {
      return !myFileTypeManager.isFileIgnored(file.getName());
    }
    return false;
  }

  public void iterateIndexableFilesIn(final VirtualFile file, final ContentIterator iterator) {
    if (!isInSet(file)) return;

    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        iterateIndexableFilesIn(child, iterator);
      }
    }
    else {
      iterator.processFile(file);
    }
  }

  public void disposeComponent() {
    // done mostly for tests. In real life this is noop, becase the set was removed on project closing
    myIndex.removeIndexableSet(this);
  }

  private class RefreshCacheUpdater implements CacheUpdater {
    public int getNumberOfPendingUpdateJobs() {
      return myIndex.getNumberOfPendingInvalidations();
    }

    public VirtualFile[] queryNeededFiles() {
      Collection<VirtualFile> files = myIndex.getFilesToUpdate(myProject);
      return VfsUtil.toVirtualFileArray(files);
    }

    public void processFile(FileContent fileContent) {
      myIndex.processRefreshedFile(myProject, fileContent);
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
  }
}
