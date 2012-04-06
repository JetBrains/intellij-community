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
package com.intellij.core.indexing;


import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexableFileSet;
import com.intellij.util.indexing.IndexingStamp;
import com.intellij.util.indexing.UnindexedFilesUpdater;

import java.util.Collection;

public class FileBasedIndexProjectHandlerJavaComponent implements IndexableFileSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.indexing.FileBasedIndexProjectHandlerJavaComponent");

  private FileBasedIndex myIndex;
  private Project myProject;
  private final UnindexedFilesUpdater myUnindexedFilesUpdater;

  public FileBasedIndexProjectHandlerJavaComponent(final FileBasedIndex index,
                                                   final Project project,
                                                   final IndexingStamp indexingStamp) {
    myIndex = index;
    myProject = project;

    //final RefreshCacheUpdater changedFilesUpdater = new RefreshCacheUpdater();
    myUnindexedFilesUpdater = new UnindexedFilesUpdater(project, index, indexingStamp);

    //final ProjectRootManagerEx rootManager,

    //startupManager.registerCacheUpdater(unindexedFilesUpdater);
    //rootManager.registerRootsChangeUpdater(unindexedFilesUpdater);
    //rootManager.registerRefreshUpdater(changedFilesUpdater);
    myIndex.registerIndexableSet(this, project);

    //projectManager.addProjectManagerListener(project, new ProjectManagerAdapter() {
    //  @Override
    //  public void projectClosing(Project project) {
    //    rootManager.unregisterRefreshUpdater(changedFilesUpdater);
    //    rootManager.unregisterRootsChangeUpdater(unindexedFilesUpdater);
    //    myIndex.removeIndexableSet(FileBasedIndexProjectHandler.this);
    //  }
    //});
  }

  public void updateCache() {
    ProgressIndicator pi =  new EmptyProgressIndicator();
    for(VirtualFile f :  myUnindexedFilesUpdater.queryNeededFiles(pi))
    {
      FileContent content = new FileContent(f);
      if (f.isValid() && !f.isDirectory()) {
        if (!doLoadContent(content)) {
          content.setEmptyContent();
        }
      }
      else
      content.setEmptyContent();
      myUnindexedFilesUpdater.processFile(content);
    }
  }

  private static boolean doLoadContent(final FileContent content) {
    try {
      content.getBytes(); // Reads the content bytes and caches them.
      return true;
    }
    catch (Throwable e) {
      LOG.error(e);
      return false;
    }
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

  @Override
  public boolean isInSet(final VirtualFile file) {
    //final ProjectFileIndex index = myRootManager.getFileIndex();
    //if (index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file)) {
    //  if (myExclusionManager != null && myExclusionManager.isExcluded(file)) return false;
    //  return !myFileTypeManager.isFileIgnored(file);
    //}
    return true;
  }

  @Override
  public void iterateIndexableFilesIn(VirtualFile file, final ContentIterator iterator) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(VirtualFile file) {

        if (!isInSet(file)) return false;
        if (!file.isDirectory()) {
          iterator.processFile(file);
        }
        return true;
      }
    });
  }
}
