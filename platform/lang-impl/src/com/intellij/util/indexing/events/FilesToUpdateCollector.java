// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

@Internal
public class FilesToUpdateCollector {
  private static final Logger LOG = Logger.getInstance(FilesToUpdateCollector.class);
  private final NotNullLazyValue<FileBasedIndexImpl> myFileBasedIndex = NotNullLazyValue.createValue(() -> (FileBasedIndexImpl)FileBasedIndex.getInstance());
  private final IntObjectMap<FileIndexingRequest> myFilesToUpdate = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  private final DirtyFiles myDirtyFiles = new DirtyFiles();

  public void scheduleForUpdate(@NotNull FileIndexingRequest request, @NotNull Set<Project> containingProjects, @NotNull Collection<Project> dirtyQueueProjects) {
    VirtualFile file = request.getFile();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!request.isDeleteRequest() && containingProjects.isEmpty()) {
        LOG.error("File without project should not be added to FilesToUpdateCollector because it will not be indexed " +
                  "(projects pick own update requests and all all delete requests from this collector). " +
                  "File=" + file.getPath());
      }
    }
    VfsEventsMerger.tryLog("ADD_TO_UPDATE", file);
    int fileId = request.getFileId();
    myDirtyFiles.addFile(dirtyQueueProjects, fileId);
    myFilesToUpdate.put(fileId, request);
  }

  public @NotNull DirtyFiles getDirtyFiles() {
    return myDirtyFiles;
  }

  public void removeScheduledFileFromUpdate(VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    FileIndexingRequest alreadyScheduledFile = myFilesToUpdate.get(fileId);
    if (alreadyScheduledFile != null && !alreadyScheduledFile.isDeleteRequest()) {
      VfsEventsMerger.tryLog("PULL_OUT_FROM_UPDATE", fileId);
      myFilesToUpdate.remove(fileId);
      myDirtyFiles.removeFile(fileId);
    }
  }

  public void removeFileIdFromFilesScheduledForUpdate(int fileId) {
    myFilesToUpdate.remove(fileId);
    myDirtyFiles.removeFile(fileId);
  }

  public void clear() {
    myDirtyFiles.clear();
    myFilesToUpdate.clear();
  }

  public boolean containsFileId(int fileId) {
    return myFilesToUpdate.containsKey(fileId);
  }

  public Iterator<@NotNull FileIndexingRequest> getFilesToUpdateAsIterator() {
    return myFilesToUpdate.values().iterator();
  }

  public Collection<FileIndexingRequest> getFilesToUpdate() {
    return myFilesToUpdate.isEmpty()
           ? Collections.emptyList()
           : Collections.unmodifiableCollection(myFilesToUpdate.values());
  }

  public boolean isScheduledForUpdate(VirtualFile file) {
    return myFilesToUpdate.containsKey(FileBasedIndex.getFileId(file));
  }
}
