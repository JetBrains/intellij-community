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

package com.intellij.util.fileIndex;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class FileIndexRefreshCacheUpdater extends VirtualFileAdapter implements CacheUpdater, VirtualFileManagerListener, Disposable {
  private final AbstractFileIndex myFileIndex;
  private final VirtualFileManagerEx myVirtualFileManager;
  private final ProjectRootManagerEx myProjectRootManager;
  private final Set<VirtualFile> myChangedFiles = new HashSet<VirtualFile>();
  private final Set<VirtualFile> myRemovedFiles = new HashSet<VirtualFile>();

  public FileIndexRefreshCacheUpdater(Project project, final AbstractFileIndex fileIndex) {
    myFileIndex = fileIndex;
    myVirtualFileManager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    myProjectRootManager = ProjectRootManagerEx.getInstanceEx(project);
    myVirtualFileManager.addVirtualFileManagerListener(this);
    myVirtualFileManager.addVirtualFileListener(this,this);
    myProjectRootManager.registerRefreshUpdater(this);
  }

  public void dispose() {
    myVirtualFileManager.removeVirtualFileManagerListener(this);
    myProjectRootManager.unregisterRefreshUpdater(this);
  }

  public void beforeRefreshStart(boolean asynchonous) {
  }

  public void afterRefreshFinish(boolean asynchonous) {
    if (!asynchonous) {
      for (VirtualFile file : myChangedFiles) {
        myFileIndex.updateIndexEntry(file);
      }
      updatingDone();
    }
  }

  public int getNumberOfPendingUpdateJobs() {
    return 0;
  }

  public VirtualFile[] queryNeededFiles() {
    return VfsUtil.toVirtualFileArray(myChangedFiles);
  }

  public void processFile(FileContent fileContent) {
    myFileIndex.updateIndexEntry(fileContent.getVirtualFile());
  }

  public void updatingDone() {
    myChangedFiles.clear();
    for (VirtualFile file : myRemovedFiles) {
      myFileIndex.removeIndexEntry(file);
    }
    myRemovedFiles.clear();
  }

  public void canceled() {
  }

  public void fileCreated(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    handleCreateDeleteFile(file, event.isFromRefresh(), true);
  }

  public void contentsChanged(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    if (myFileIndex.belongs(file)) {
      if (event.isFromRefresh()) {
        myChangedFiles.add(file);
      }
      else {
        myFileIndex.queueEntryUpdate(file);
      }
    }
  }

  private void handleCreateDeleteFile(final VirtualFile file, final boolean fromRefresh, final boolean create) {
    if (!myFileIndex.getProjectFileIndex().isInContent(file)) {
      return;
    }
    if (file.isDirectory()) {
      final Collection<VirtualFile> children = create ? Arrays.asList(file.getChildren()) : ((NewVirtualFile)file).getCachedChildren();
      for (VirtualFile child : children) {
        handleCreateDeleteFile(child, fromRefresh, create);
      }
      return;
    }

    if (myFileIndex.belongs(file)) {
      if (fromRefresh) {
        if (create) {
          myChangedFiles.add(file);
        }
        else {
          myRemovedFiles.add(file);
        }
      }
      else {
        if (create) {
          myFileIndex.updateIndexEntry(file);
        }
        else {
          myFileIndex.removeIndexEntry(file);
        }
      }
    }
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      beforeFileDeletion(event);
    }
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    beforeFileDeletion(event);
  }


  public void fileMoved(VirtualFileMoveEvent event) {
    fileCreated(event);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    final VirtualFile file = event.getFile();
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) && myFileIndex.belongs(file)) {
      fileCreated(event);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    handleCreateDeleteFile(file, event.isFromRefresh(), false);
  }
}
