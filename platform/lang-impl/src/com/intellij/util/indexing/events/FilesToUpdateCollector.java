// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.indexing.events.ChangedFilesCollector.CLEAR_NON_INDEXABLE_FILE_DATA;
import static com.intellij.util.indexing.events.ChangedFilesCollector.removeFromDirtyFiles;

public class FilesToUpdateCollector {
  private static final Logger LOG = Logger.getInstance(FilesToUpdateCollector.class);
  private final FileBasedIndexImpl myFileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
  private final IntObjectMap<VirtualFile> myFilesToUpdate = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  private final List<Pair<Project, ConcurrentBitSet>> myDirtyFiles;
  private final ConcurrentBitSet myDirtyFilesWithoutProject;

  public FilesToUpdateCollector(@NotNull List<Pair<Project, ConcurrentBitSet>> dirtyFiles, @NotNull ConcurrentBitSet dirtyFilesWithoutProject) {
    myDirtyFiles = dirtyFiles;
    myDirtyFilesWithoutProject = dirtyFilesWithoutProject;
  }

  public void scheduleForUpdate(@NotNull VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    if (!(file instanceof DeletedVirtualFileStub)) {
      Set<Project> projects = myFileBasedIndex.getContainingProjects(file);
      if (projects.isEmpty()) {
        removeNonIndexableFileData(file, fileId);
        myFileBasedIndex.getIndexableFilesFilterHolder().removeFile(fileId);
        return;
      }
    }

    VfsEventsMerger.tryLog("ADD_TO_UPDATE", file);
    myFilesToUpdate.put(fileId, file);
  }

  private void removeNonIndexableFileData(@NotNull VirtualFile file, int fileId) {
    if (CLEAR_NON_INDEXABLE_FILE_DATA) {
      List<ID<?, ?>> extensions = getIndexedContentDependentExtensions(fileId);
      if (!extensions.isEmpty()) {
        myFileBasedIndex.removeDataFromIndicesForFile(fileId, file, "non_indexable_file");
      }
      IndexingFlag.cleanProcessingFlag(file);
    }
    else if (ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
      checkNotIndexedByContentBasedIndexes(file, fileId);
    }
  }

  private void checkNotIndexedByContentBasedIndexes(@NotNull VirtualFile file, int fileId) {
    List<ID<?, ?>> contentDependentIndexes = getIndexedContentDependentExtensions(fileId);
    if (!contentDependentIndexes.isEmpty()) {
      LOG.error("indexes " + contentDependentIndexes + " will not be updated for file = " + file + ", id = " + fileId);
    }
  }

  private @NotNull List<ID<?, ?>> getIndexedContentDependentExtensions(int fileId) {
    List<ID<?, ?>> indexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);
    RegisteredIndexes registeredIndexes = myFileBasedIndex.getRegisteredIndexes();
    List<ID<?, ?>> contentDependentIndexes;
    if (registeredIndexes == null) {
      Set<? extends ID<?, ?>> allContentDependentIndexes = FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList().stream()
        .filter(ex -> ex.dependsOnFileContent())
        .map(ex -> ex.getName())
        .collect(Collectors.toSet());
      contentDependentIndexes = ContainerUtil.filter(indexedStates, id -> !allContentDependentIndexes.contains(id));
    }
    else {
      contentDependentIndexes = ContainerUtil.filter(indexedStates, id -> {
        return registeredIndexes.isContentDependentIndex(id);
      });
    }
    return contentDependentIndexes;
  }

  public void removeScheduledFileFromUpdate(VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    VirtualFile alreadyScheduledFile = myFilesToUpdate.get(fileId);
    if (!(alreadyScheduledFile instanceof DeletedVirtualFileStub)) {
      VfsEventsMerger.tryLog("PULL_OUT_FROM_UPDATE", file);
      myFilesToUpdate.remove(fileId);
      removeFromDirtyFiles(fileId, myDirtyFilesWithoutProject, myDirtyFiles);
    }
  }

  public void removeFileIdFromFilesScheduledForUpdate(int fileId) {
    myFilesToUpdate.remove(fileId);
    removeFromDirtyFiles(fileId, myDirtyFilesWithoutProject, myDirtyFiles);
  }

  public void clearFilesToUpdate() {
    myFilesToUpdate.clear();
  }

  public boolean containsFileId(int fileId) {
    return myFilesToUpdate.containsKey(fileId);
  }

  public Iterator<@NotNull VirtualFile> getFilesToUpdate() {
    return myFilesToUpdate.values().iterator();
  }

  public Collection<VirtualFile> getAllFilesToUpdate() {
    return myFilesToUpdate.isEmpty()
           ? Collections.emptyList()
           : Collections.unmodifiableCollection(myFilesToUpdate.values());
  }

  public boolean isScheduledForUpdate(VirtualFile file) {
    return myFilesToUpdate.containsKey(FileBasedIndex.getFileId(file));
  }
}
