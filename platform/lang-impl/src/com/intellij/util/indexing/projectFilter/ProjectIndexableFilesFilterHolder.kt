// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.indexing.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal enum class FileAddStatus {
  ADDED, PRESENT, SKIPPED
}

internal sealed class ProjectIndexableFilesFilterHolder {
  abstract fun getProjectIndexableFiles(project: Project): IdFilter?

  /**
   * @returns true if fileId already contained in or was added to one of project filters
   */
  abstract fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): Boolean

  abstract fun addFileId(fileId: Int, project: Project)

  abstract fun entireProjectUpdateStarted(project: Project)

  abstract fun removeFile(fileId: Int)

  abstract fun findProjectForFile(fileId: Int): Project?

  abstract fun runHealthCheck()
}

internal class IncrementalProjectIndexableFilesFilterHolder : ProjectIndexableFilesFilterHolder() {
  private val myProjectFilters: ConcurrentMap<Project, IncrementalProjectIndexableFilesFilter> = ConcurrentHashMap()

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        myProjectFilters.remove(project)
      }
    })
  }

  override fun getProjectIndexableFiles(project: Project): IdFilter? {
    if (!UnindexedFilesScanner.isProjectContentFullyScanned(project) || UnindexedFilesUpdater.isIndexUpdateInProgress(project)) {
      return null
    }
    return getFilter(project)
  }

  override fun entireProjectUpdateStarted(project: Project) {
    assert(UnindexedFilesUpdater.isIndexUpdateInProgress(project))

    getFilter(project)?.resetFileIds()
  }

  private fun getFilter(project: Project) = myProjectFilters.computeIfAbsent(project) {
    if (it.isDisposed) null else IncrementalProjectIndexableFilesFilter()
  }

  override fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): Boolean {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    val statuses = myProjectFilters.map { (p, filter) ->
      filter.ensureFileIdPresent(fileId) {
        matchedProjects.contains(p)
      }
    }

    return statuses.any { it }
  }

  override fun addFileId(fileId: Int, project: Project) {
    myProjectFilters[project]?.ensureFileIdPresent(fileId) { true }
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
          error.fix()
        }

        val message = StringUtil.first(errors!!.take(100).joinToString(", ") { ReadAction.nonBlocking(Callable { it.presentableText }).executeSynchronously() },
          300,
          true)
        FileBasedIndexImpl.LOG.error("Project indexable filter health check errors: $message")

      }
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
  }

  private fun runHealthCheck(project: Project, filter: IncrementalProjectIndexableFilesFilter): List<HealthCheckError> {
    return filter.runAndCheckThatNoChangesHappened {
      val errors = mutableListOf<HealthCheckError>()
      val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
      index.iterateIndexableFiles(ContentIterator {
        if (it is VirtualFileWithId) {
          val fileId = it.id
          if (!filter.containsFileId(fileId)) {
            errors.add(HealthCheckError(project, it, fileId, filter))
          }
        }
        true
      }, project, ProgressManager.getInstance().progressIndicator)
      errors
    }
  }

  private class HealthCheckError(private val project: Project,
                                 private val virtualFile: VirtualFile,
                                 private val fileId: Int,
                                 private val filter: IncrementalProjectIndexableFilesFilter) {
    val presentableText: String
      get() = "file ${virtualFile.path} not found in ${project.name}"

    fun fix() {
      filter.ensureFileIdPresent(fileId) { true }
    }
  }
}