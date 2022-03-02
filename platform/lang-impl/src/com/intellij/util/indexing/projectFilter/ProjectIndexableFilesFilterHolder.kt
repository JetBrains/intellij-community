// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentMap

internal enum class FileAddStatus {
  ADDED, PRESENT, SKIPPED
}

internal sealed class ProjectIndexableFilesFilterHolder {
  abstract fun getProjectIndexableFiles(project: Project): IdFilter?

  abstract fun addFileId(fileId: Int, projects: () -> Set<Project>): FileAddStatus

  abstract fun addFileId(fileId: Int, project: Project): FileAddStatus

  abstract fun entireProjectUpdateStarted(project: Project)

  abstract fun entireProjectUpdateFinished(project: Project)

  abstract fun removeFile(fileId: Int)

  abstract fun findProjectForFile(fileId: Int): Project?

  abstract fun runHealthCheck()
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

  override fun addFileId(fileId: Int, projects: () -> Set<Project>): FileAddStatus {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    val statuses = myProjectFilters.map { (p, filter) ->
      filter.ensureFileIdPresent(fileId) {
        matchedProjects.contains(p)
      }
    }

    if (statuses.all { it == FileAddStatus.SKIPPED }) return FileAddStatus.SKIPPED
    if (statuses.any { it == FileAddStatus.ADDED }) return FileAddStatus.ADDED
    return FileAddStatus.PRESENT
  }

  override fun addFileId(fileId: Int, project: Project): FileAddStatus {
    return myProjectFilters.get(project)!!.ensureFileIdPresent(fileId) { true }
  }

  override fun removeFile(fileId: Int) {
    for (filter in myProjectFilters.values) {
      filter.removeFileId(fileId)
    }
  }

  override fun findProjectForFile(fileId: Int): Project? {
    for ((project, filter) in myProjectFilters) {
      if (filter.containsFileId(fileId)) {
        return project
      }
    }
    return null
  }

  override fun runHealthCheck() {
    try {
      for ((project, filter) in myProjectFilters) {
        var errors: List<HealthCheckError>? = null
        ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
          if (DumbService.isDumb(project)) return@runInReadActionWithWriteActionPriority
          errors = runHealthCheck(project, filter)
        }

        if (errors.isNullOrEmpty()) continue

        for (error in errors!!) {
          error.fix(filter)
        }

        val message = StringUtil.first(errors!!.map { ReadAction.nonBlocking(Callable { it.presentableText }) }.joinToString(", "),
                                       300,
                                       true)
        FileBasedIndexImpl.LOG.error("Project indexable filter health check errors: $message")

      }
    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
    catch (_: ProcessCanceledException) {

    }
  }

  private fun runHealthCheck(project: Project, filter: IncrementalProjectIndexableFilesFilter): List<HealthCheckError> {
    val errors = mutableListOf<HealthCheckError>()
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    index.iterateIndexableFiles(ContentIterator {
      if (it is VirtualFileWithId) {
        val fileId = it.id
        if (!filter.containsFileId(fileId)) {
          filter.ensureFileIdPresent(fileId) { true }
        }
      }
      true
    }, project, ProgressManager.getInstance().progressIndicator)
    return errors
  }

  private class HealthCheckError(private val project: Project, private val virtualFile: VirtualFile) {
    val presentableText: String
      get() = "file ${virtualFile.path} not found in ${project.name}"

    fun fix(filter: IncrementalProjectIndexableFilesFilter) {
      filter.ensureFileIdPresent((virtualFile as VirtualFileWithId).id) { true }
    }

  }
}