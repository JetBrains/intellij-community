/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class IndexedFilesListener extends VirtualFileAdapter implements BulkFileListener {
  private final ManagingFS myManagingFS = ManagingFS.getInstance();
  @Nullable private final String myConfigPath;
  @Nullable private final String myLogPath;

  public IndexedFilesListener() {
    myConfigPath = calcConfigPath(PathManager.getConfigPath());
    myLogPath = calcConfigPath(PathManager.getLogPath());
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void fileCreated(@NotNull final VirtualFileEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void fileCopied(@NotNull final VirtualFileCopyEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void beforeFileDeletion(@NotNull final VirtualFileEvent event) {
    invalidateIndicesRecursively(event.getFile(), false);
  }

  @Override
  public void beforeContentsChange(@NotNull final VirtualFileEvent event) {
    invalidateIndicesRecursively(event.getFile(), true);
  }

  @Override
  public void contentsChanged(@NotNull final VirtualFileEvent event) {
    buildIndicesForFileRecursively(event.getFile(), true);
  }

  @Override
  public void beforePropertyChange(@NotNull final VirtualFilePropertyEvent event) {
    String propertyName = event.getPropertyName();

    if (propertyName.equals(VirtualFile.PROP_NAME)) {
      // indexes may depend on file name
      // name change may lead to filetype change so the file might become not indexable
      // in general case have to 'unindex' the file and index it again if needed after the name has been changed
      invalidateIndicesRecursively(event.getFile(), false);
    } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
      invalidateIndicesRecursively(event.getFile(), true);
    }
  }

  @Override
  public void propertyChanged(@NotNull final VirtualFilePropertyEvent event) {
    String propertyName = event.getPropertyName();
    if (propertyName.equals(VirtualFile.PROP_NAME)) {
      // indexes may depend on file name
      buildIndicesForFileRecursively(event.getFile(), false);
    } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
      buildIndicesForFileRecursively(event.getFile(), true);
    }
  }

  protected void buildIndicesForFileRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    if (file.isDirectory()) {
      final ContentIterator iterator = fileOrDir -> {
        buildIndicesForFile(fileOrDir, contentChange);
        return true;
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      buildIndicesForFile(file, contentChange);
    }
  }

  protected boolean invalidateIndicesForFile(VirtualFile file, boolean contentChange) {
    if (isUnderConfigOrSystem(file)) {
      return false;
    }
    if (file.isDirectory()) {
      doInvalidateIndicesForFile(file, contentChange);
      if (!FileBasedIndexImpl.isMock(file) && !myManagingFS.wereChildrenAccessed(file)) {
        return false;
      }
    }
    else {
      doInvalidateIndicesForFile(file, contentChange);
    }
    return true;
  }

  protected abstract void iterateIndexableFiles(VirtualFile file, ContentIterator iterator);
  protected abstract void buildIndicesForFile(VirtualFile file, boolean contentChange);
  protected abstract void doInvalidateIndicesForFile(VirtualFile file, boolean contentChange);

  protected void invalidateIndicesRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return invalidateIndicesForFile(file, contentChange);
      }

      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : null;
      }
    });
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      BulkVirtualFileListenerAdapter.fireBefore(this, event);
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      BulkVirtualFileListenerAdapter.fireAfter(this, event);
    }
  }

  @Nullable
  private static String calcConfigPath(@NotNull String path) {
    try {
      final String _path = FileUtil.toSystemIndependentName(new File(path).getCanonicalPath());
      return _path.endsWith("/") ? _path : _path + "/";
    }
    catch (IOException e) {
      FileBasedIndexImpl.LOG.info(e);
      return null;
    }
  }

  private boolean isUnderConfigOrSystem(@NotNull VirtualFile file) {
    final String filePath = file.getPath();
    return myConfigPath != null && FileUtil.startsWith(filePath, myConfigPath) ||
           myLogPath != null && FileUtil.startsWith(filePath, myLogPath);
  }

  static final short  ADD_TO_INDICES = 1;
  static final short  REMOVE_FROM_INDICES = 2;
  static final short  UPDATE_INDICES_CONTENT_CHANGED = 4;
  static final short  REMOVE_FROM_INDICES_CONTENT_CHANGED = 8;

  public static class ChangeInfo {
    final VirtualFile file;
    private short indexOperation;

    ChangeInfo(VirtualFile file, short indexOperation) {
      this.file = file;
      this.indexOperation = indexOperation;
    }

    void changeOperation(int operation) {
      if (operation == REMOVE_FROM_INDICES) {
        indexOperation = 0;
      }
      indexOperation |= operation;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("file: ").append(file.getPath()).append("\n")
        .append("operation: ");
      if ((indexOperation & REMOVE_FROM_INDICES) != 0) builder.append("REMOVE ");
      if ((indexOperation & ADD_TO_INDICES) != 0) builder.append("ADD ");
      if ((indexOperation & REMOVE_FROM_INDICES_CONTENT_CHANGED) != 0) builder.append("UPDATE-REMOVE ");
      if ((indexOperation & UPDATE_INDICES_CONTENT_CHANGED) != 0) builder.append("UPDATE ");
      return builder.toString().trim();
    }

    boolean isBeforeContentChangedUpdate() {
      return (indexOperation & REMOVE_FROM_INDICES_CONTENT_CHANGED) != 0;
    }

    boolean isContentChangedUpdate() {
      return (indexOperation & UPDATE_INDICES_CONTENT_CHANGED) != 0;
    }

    boolean isRemoveFromIndices() {
      return (indexOperation & REMOVE_FROM_INDICES) != 0;
    }

    boolean isAddToIndices() {
      return (indexOperation & ADD_TO_INDICES) != 0;
    }
  }

  public void recordFileScheduledForIndexing(int fileId, VirtualFile file, boolean contentChange) {
    updateChange(fileId, file, contentChange ? UPDATE_INDICES_CONTENT_CHANGED : ADD_TO_INDICES);
  }

  public void recordFileScheduledForInvalidation(int fileId, VirtualFile file, boolean contentChanged) {
    updateChange(fileId, file, contentChanged ? REMOVE_FROM_INDICES_CONTENT_CHANGED : REMOVE_FROM_INDICES);
  }

  private void updateChange(int fileId, VirtualFile file, short mask) {
    if (DebugAssertions.DEBUG) assert ApplicationManager.getApplication().isDispatchThread();
    ChangeInfo changeInfo = myChangeInfos.get(fileId);
    if (changeInfo == null) myChangeInfos.put(fileId, new ChangeInfo(file, mask));
    else changeInfo.changeOperation(mask);
  }

  final ConcurrentIntObjectMap<ChangeInfo> myChangeInfos = ContainerUtil.createConcurrentIntObjectMap();

  @TestOnly
  public void iterateChanges(Processor<ChangeInfo> changeInfoProcessor) {
    ContainerUtil.process(myChangeInfos.values(), changeInfoProcessor);
    myChangeInfos.clear();
  }
}