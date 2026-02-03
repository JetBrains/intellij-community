// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;

@Internal
public class FilesToUpdateCollector {
  private static final Logger LOG = Logger.getInstance(FilesToUpdateCollector.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, 1000);

  //files are duplicated here: they are both in filesToUpdate and in dirtyFiles (already sorted by
  // projects). Maybe it is worth merging that functionality -- so we could always query dirty
  // files per project?
  private final IntObjectMap<FileIndexingRequest> myFilesToUpdate = createConcurrentIntObjectMap();

  private final DirtyFiles myDirtyFiles = new DirtyFiles();

  /**
   * @param containingProjects projects request.file is belong to. Used mostly for diagnostics
   * @param dirtyQueueProjects projects request.file is belong to. Used to actually put the file into
   *                           apt queue(s)
   */
  public void scheduleForUpdate(@NotNull FileIndexingRequest request,
                                @NotNull Set<Project> containingProjects,
                                @NotNull Collection<? extends Project> dirtyQueueProjects) {
    if (!request.isDeleteRequest() && request.getFile().isDirectory()) {
      THROTTLED_LOG.warn("Directory was passed for indexing unexpectedly: " + request.getFile().getPath(), new Throwable());
    }
    VirtualFile file = request.getFile();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!request.isDeleteRequest() && containingProjects.isEmpty()) {
        LOG.error("File without project should not be added to FilesToUpdateCollector because it will not be indexed " +
                  "(projects pick own update requests and all all delete requests from this collector). " +
                  "File=" + file.getPath());
      }
    }
    IndexingEventsLogger.tryLog("ADD_TO_UPDATE", file);
    int fileId = request.getFileId();
    myDirtyFiles.addFile(dirtyQueueProjects, fileId);
    myFilesToUpdate.put(fileId, request);
  }

  public @NotNull DirtyFiles getDirtyFiles() {
    return myDirtyFiles;
  }

  public void removeScheduledFileFromUpdate(@NotNull VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    FileIndexingRequest alreadyScheduledFile = myFilesToUpdate.get(fileId);
    if (alreadyScheduledFile != null && !alreadyScheduledFile.isDeleteRequest()) {
      IndexingEventsLogger.tryLog("PULL_OUT_FROM_UPDATE", fileId);
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

  public Iterator<@NotNull FileIndexingRequest> getFilesToUpdateAsIterator() {
    return myFilesToUpdate.values().iterator();
  }

  public Collection<FileIndexingRequest> getFilesToUpdate() {
    return myFilesToUpdate.isEmpty()
           ? Collections.emptyList()
           : Collections.unmodifiableCollection(myFilesToUpdate.values());
  }

  public boolean isScheduledForUpdate(VirtualFile file) {
    return containsFileId(FileBasedIndex.getFileId(file));
  }

  public boolean containsFileId(int fileId) {
    return myFilesToUpdate.containsKey(fileId);
  }
}
