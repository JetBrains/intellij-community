// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.IndexUpToDateCheckIn.isUpToDateCheckEnabled
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.indexing.UnindexedFilesUpdaterListener
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

internal sealed class ProjectIndexableFilesFilterHolder {
  abstract fun getProjectIndexableFiles(project: Project): IdFilter?
}

internal class IncrementalProjectIndexableFilesFilterHolder : ProjectIndexableFilesFilterHolder() {
  private val myProjectFilters: ConcurrentMap<Project, IncrementalProjectIndexableFilesFilter> = ConcurrentFactoryMap.createMap {
    proj -> IncrementalProjectIndexableFilesFilter(proj)
  }

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosed(project: Project) {
        myProjectFilters.remove(project)
      }
    })
  }

  override fun getProjectIndexableFiles(project: Project): IdFilter? {
    return myProjectFilters[project]
  }

  fun dropMemorySnapshot(project: Project) {
    myProjectFilters[project]?.drop()
  }

  fun addFileId(fileId: Int, projects: () -> Set<Project>) {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    for ((p, filter) in myProjectFilters) {
      filter.ensureFileIdPresent(fileId) {
        matchedProjects.contains(p)
      }
    }
  }

  fun removeFile(fileId: Int) {
    for (filter in myProjectFilters.values) {
      filter.removeFileId(fileId)
    }
  }
}

internal class ProjectIndexableFilesFilterHolderImpl(private val myFileBasedIndex: FileBasedIndexImpl): ProjectIndexableFilesFilterHolder() {
  private val myCalcIndexableFilesLock: Lock = ReentrantLock()
  private val myProjectsBeingUpdated: MutableSet<Project> = ContainerUtil.newConcurrentSet()

  init {
    val unindexedFilesUpdaterListener: UnindexedFilesUpdaterListener = object : UnindexedFilesUpdaterListener {
      override fun updateStarted(project: Project) {
        myProjectsBeingUpdated.add(project)
      }

      override fun updateFinished(project: Project) {
        myProjectsBeingUpdated.remove(project)
      }
    }
    ApplicationManager.getApplication().messageBus.connect().subscribe(UnindexedFilesUpdaterListener.TOPIC,
                                                                       unindexedFilesUpdaterListener)
  }

  override fun getProjectIndexableFiles(project: Project): ProjectIndexableFilesFilter? {
    if (myProjectsBeingUpdated.contains(project) || !UnindexedFilesUpdater.isProjectContentFullyScanned(project)) return null
    var reference: SoftReference<ProjectIndexableFilesFilter>? = project.getUserData(ourProjectFilesSetKey)
    var data = com.intellij.reference.SoftReference.dereference(reference)
    val currentFileModCount = myFileBasedIndex.filesModCount
    if (data != null && data.modificationCount == currentFileModCount) return data
    return if (myCalcIndexableFilesLock.tryLock()) { // make best effort for calculating filter
      try {
        reference = project.getUserData(ourProjectFilesSetKey)
        data = com.intellij.reference.SoftReference.dereference(reference)
        if (data != null) {
          if (data.modificationCount == currentFileModCount) {
            return data
          }
        }
        else if (!isUpToDateCheckEnabled()) {
          return null
        }
        val start = System.currentTimeMillis()
        val fileSet: IntList = IntArrayList()
        myFileBasedIndex.iterateIndexableFiles({ fileOrDir: VirtualFile? ->
                                                 if (fileOrDir is VirtualFileWithId) {
                                                   fileSet.add((fileOrDir as VirtualFileWithId).id)
                                                 }
                                                 true
                                               }, project, null)
        val filter = ProjectIndexableFilesFilter(fileSet, currentFileModCount)
        project.putUserData(ourProjectFilesSetKey, SoftReference(filter))
        val finish = System.currentTimeMillis()
        LOG.debug(fileSet.size.toString() + " files iterated in " + (finish - start) + " ms")
        filter
      }
      finally {
        myCalcIndexableFilesLock.unlock()
      }
    }
    else null
    // ok, no filtering
  }

  companion object {
    private val LOG = Logger.getInstance(ProjectIndexableFilesFilterHolder::class.java)
    private val ourProjectFilesSetKey: Key<SoftReference<ProjectIndexableFilesFilter>> = Key.create("projectFiles")
  }
}