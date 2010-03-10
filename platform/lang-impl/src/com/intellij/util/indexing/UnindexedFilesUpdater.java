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

package com.intellij.util.indexing;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 29, 2008
 */
public class UnindexedFilesUpdater implements CacheUpdater {
  private final FileBasedIndex myIndex;
  private final Project myProject;

  public UnindexedFilesUpdater(final Project project, FileBasedIndex index) {
    myIndex = index;
    myProject = project;
  }

  public int getNumberOfPendingUpdateJobs() {
    return myIndex.getNumberOfPendingInvalidations();
  }

  public VirtualFile[] queryNeededFiles() {
    CollectingContentIterator finder = myIndex.createContentIterator();
    FileBasedIndex.iterateIndexableFiles(finder, myProject);
    List<VirtualFile> files = finder.getFiles();
    return VfsUtil.toVirtualFileArray(files);
  }

  public void processFile(final FileContent fileContent) {
    myIndex.indexFileContent(myProject, fileContent);
    IndexingStamp.flushCache();
  }

  public void updatingDone() {
    myIndex.flushCaches();
  }

  public void canceled() {
    myIndex.flushCaches();
  }
}
