// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater
import java.util.concurrent.ConcurrentMap

internal sealed class ProjectIndexableFilesFilterHolder {
  abstract fun getProjectIndexableFiles(project: Project): IdFilter?

  abstract fun addFileId(fileId: Int, projects: () -> Set<Project>)

  abstract fun addFileId(fileId: Int, project: Project): Boolean

  abstract fun entireProjectUpdateStarted(project: Project)

  abstract fun entireProjectUpdateFinished(project: Project)

  abstract fun removeFile(fileId: Int)
}

internal class IncrementalProjectIndexableFilesFilterHolder : ProjectIndexableFilesFilterHolder() {
  private val myProjectFilters: ConcurrentMap<Project, IncrementalProjectIndexableFilesFilter> = ConcurrentFactoryMap.createMap { IncrementalProjectIndexableFilesFilter() }

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosed(project: Project) {
        myProjectFilters.remove(project)
      }
    })
  }

  override fun getProjectIndexableFiles(project: Project): IdFilter? {
    if (!UnindexedFilesUpdater.isProjectContentFullyScanned(project) || UnindexedFilesUpdater.isIndexUpdateInProgress(project)) {
      return null
    }
    return myProjectFilters[project]
  }

  override fun entireProjectUpdateStarted(project: Project) {
    assert(UnindexedFilesUpdater.isIndexUpdateInProgress(project))

    myProjectFilters[project]?.memoizeAndResetFileIds()
  }

  override fun entireProjectUpdateFinished(project: Project) {
    assert(UnindexedFilesUpdater.isIndexUpdateInProgress(project))

    myProjectFilters[project]?.resetPreviousFileIds()
  }

  override fun addFileId(fileId: Int, projects: () -> Set<Project>) {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    for ((p, filter) in myProjectFilters) {
      filter.ensureFileIdPresent(fileId) {
        matchedProjects.contains(p)
      }
    }
  }

  override fun addFileId(fileId: Int, project: Project): Boolean {
    return myProjectFilters.get(project)!!.ensureFileIdPresent(fileId) { true }
  }

  override fun removeFile(fileId: Int) {
    for (filter in myProjectFilters.values) {
      filter.removeFileId(fileId)
    }
  }
}