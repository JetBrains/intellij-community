// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexUpToDateCheckIn;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.indexing.UnindexedFilesUpdaterListener;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ApiStatus.Internal
public final class ProjectIndexableFilesFilterHolder {
  private final static Logger LOG = Logger.getInstance(ProjectIndexableFilesFilterHolder.class);
  private final @NotNull FileBasedIndexImpl myFileBasedIndex;
  private final Lock myCalcIndexableFilesLock = new ReentrantLock();
  private final Set<Project> myProjectsBeingUpdated = ContainerUtil.newConcurrentSet();
  private static final Key<SoftReference<ProjectIndexableFilesFilter>> ourProjectFilesSetKey = Key.create("projectFiles");

  public ProjectIndexableFilesFilterHolder(@NotNull FileBasedIndexImpl fileBasedIndex) {
    myFileBasedIndex = fileBasedIndex;
    UnindexedFilesUpdaterListener unindexedFilesUpdaterListener = new UnindexedFilesUpdaterListener() {
      @Override
      public void updateStarted(@NotNull Project project) {
        myProjectsBeingUpdated.add(project);
      }

      @Override
      public void updateFinished(@NotNull Project project) {
        myProjectsBeingUpdated.remove(project);
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(UnindexedFilesUpdaterListener.TOPIC,
                                                                            unindexedFilesUpdaterListener);
  }

  @Nullable
  public ProjectIndexableFilesFilter projectIndexableFiles(@Nullable Project project) {
    if (project == null || project.isDefault() || myFileBasedIndex.getChangedFilesCollector().isUpdateInProgress()) return null;
    if (myProjectsBeingUpdated.contains(project) || !UnindexedFilesUpdater.isProjectContentFullyScanned(project)) return null;

    SoftReference<ProjectIndexableFilesFilter> reference = project.getUserData(ourProjectFilesSetKey);
    ProjectIndexableFilesFilter data = com.intellij.reference.SoftReference.dereference(reference);
    int currentFileModCount = myFileBasedIndex.getFilesModCount();
    if (data != null && data.getModificationCount() == currentFileModCount) return data;

    if (myCalcIndexableFilesLock.tryLock()) { // make best effort for calculating filter
      try {
        reference = project.getUserData(ourProjectFilesSetKey);
        data = com.intellij.reference.SoftReference.dereference(reference);
        if (data != null) {
          if (data.getModificationCount() == currentFileModCount) {
            return data;
          }
        }
        else if (!IndexUpToDateCheckIn.isUpToDateCheckEnabled()) {
          return null;
        }

        long start = System.currentTimeMillis();

        IntList fileSet = new IntArrayList();
        myFileBasedIndex.iterateIndexableFiles(fileOrDir -> {
          if (fileOrDir instanceof VirtualFileWithId) {
            fileSet.add(((VirtualFileWithId)fileOrDir).getId());
          }
          return true;
        }, project, null);
        ProjectIndexableFilesFilter filter = new ProjectIndexableFilesFilter(fileSet, currentFileModCount);
        project.putUserData(ourProjectFilesSetKey, new SoftReference<>(filter));

        long finish = System.currentTimeMillis();
        LOG.debug(fileSet.size() + " files iterated in " + (finish - start) + " ms");

        return filter;
      }
      finally {
        myCalcIndexableFilesLock.unlock();
      }
    }
    return null; // ok, no filtering
  }
}
