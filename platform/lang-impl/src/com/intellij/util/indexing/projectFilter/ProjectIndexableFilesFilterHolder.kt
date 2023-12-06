// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.*
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilter.HealthCheckError
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@JvmField
val USE_CACHING_FILTER = SystemProperties.getBooleanProperty("idea.index.use.caching.indexable.files.filter", false)

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
  private val myProjectFilters: ConcurrentMap<Project, ProjectIndexableFilesFilter> = ConcurrentHashMap()

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
    if (it.isDisposed) null
    else if (USE_CACHING_FILTER) CachingProjectIndexableFilesFilter(project)
    else IncrementalProjectIndexableFilesFilter()
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
    if (!IndexInfrastructure.hasIndices()) return

    try {
      for ((project, filter) in myProjectFilters) {
        var errors: List<HealthCheckError>? = null
        ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
          if (DumbService.isDumb(project)) return@runInReadActionWithWriteActionPriority
          errors = filter.runHealthCheck(project)
        }

        if (errors.isNullOrEmpty()) continue

        for (error in errors!!) {
          error.fix()
        }

        val summary = errors!!
          .groupBy { it::class.java }
          .entries.joinToString("\n") { (clazz, e) ->
            "${e.size} ${clazz.simpleName} errors. Examples:\n" + e.take(10).joinToString("\n") { error ->
              ReadAction.nonBlocking(Callable { error.presentableText }).executeSynchronously()
            }
          }

        FileBasedIndexImpl.LOG.warn("${filter.javaClass.simpleName} health check found ${errors!!.size} errors in project ${project.name}:\n$summary")
      }
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
  }
}