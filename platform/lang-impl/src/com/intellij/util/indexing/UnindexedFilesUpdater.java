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
package com.intellij.util.indexing;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 * @since Jan 29, 2008
 */
public class UnindexedFilesUpdater implements CacheUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.UnindexedFilesUpdater");

  private final FileBasedIndexImpl myIndex;
  private final Project myProject;
  private final AtomicBoolean myFinishedUpdate = new AtomicBoolean();
  private long myStarted;

  public UnindexedFilesUpdater(final Project project, FileBasedIndexImpl index) {
    myIndex = index;
    myProject = project;
  }

  @Override
  public int getNumberOfPendingUpdateJobs() {
    return myIndex.getNumberOfPendingInvalidations();
  }

  @Override
  public VirtualFile[] queryNeededFiles(ProgressIndicator indicator) {
    myIndex.filesUpdateStarted(myProject);
    CollectingContentIterator finder = myIndex.createContentIterator(indicator);
    long l = System.currentTimeMillis();
    myIndex.iterateIndexableFiles(finder, myProject, indicator);
    myIndex.filesUpdateEnumerationFinished();

    LOG.info("Indexable files iterated in " + (System.currentTimeMillis() - l) + " ms");
    List<VirtualFile> files = finder.getFiles();
    LOG.info("Unindexed files update started: " + files.size() + " files to update");
    myFinishedUpdate.set(false);
    myStarted = System.currentTimeMillis();
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public void processFile(final FileContent fileContent) {
    try {
      myIndex.indexFileContent(myProject, fileContent);
    } finally {
      IndexingStamp.flushCache(fileContent.getVirtualFile());
    }
  }

  @Override
  public void updatingDone() {
    if (myFinishedUpdate.compareAndSet(false, true)) {
      myIndex.filesUpdateFinished(myProject);
      LOG.info("Unindexed files update done in " + (System.currentTimeMillis() - myStarted) + " ms");
    }
  }

  @Override
  public void canceled() {
    if (myFinishedUpdate.compareAndSet(false, true)) {
      myIndex.filesUpdateFinished(myProject);
      LOG.info("Unindexed files update canceled");
    }
  }
}
