// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.indexing.*
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilter.HealthCheckError
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@JvmField
val USE_CACHING_FILTER = SystemProperties.getBooleanProperty("idea.index.use.caching.indexable.files.filter", false)

@JvmField
val USE_PERSISTENT_FILTER = SystemProperties.getBooleanProperty("idea.index.use.persistent.indexable.files.filter", true)

internal sealed interface ProjectIndexableFilesFilterHolder {
  fun getProjectIndexableFiles(project: Project): IdFilter?

  /**
   * @returns true if fileId already contained in or was added to one of project filters
   */
  fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): List<Project>

  fun addFileId(fileId: Int, project: Project)

  fun resetFileIds(project: Project)

  fun removeFile(fileId: Int)

  fun findProjectForFile(fileId: Int): Project?

  fun findProjectsForFile(fileId: Int): Set<Project>

  fun runHealthCheck()

  fun onProjectClosing(project: Project)

  fun onProjectOpened(project: Project)

  /**
   * This is a temp method
   */
  fun wasDataLoadedFromDisk(project: Project): Boolean
}

internal class IncrementalProjectIndexableFilesFilterHolder : ProjectIndexableFilesFilterHolder {
  private val myProjectFilters: ConcurrentMap<Project, ProjectIndexableFilesFilter> = ConcurrentHashMap()

  override fun onProjectClosing(project: Project) {
    myProjectFilters.remove(project)?.onProjectClosing(project)
  }

  override fun onProjectOpened(project: Project) {
    val factory = if (USE_PERSISTENT_FILTER) PersistentProjectIndexableFilesFilterFactory()
    else if (USE_CACHING_FILTER) CachingProjectIndexableFilesFilterFactory()
    else IncrementalProjectIndexableFilesFilterFactory()

    myProjectFilters[project] = factory.create(project)
  }

  override fun wasDataLoadedFromDisk(project: Project): Boolean {
    return myProjectFilters[project]?.wasDataLoadedFromDisk ?: false
  }

  override fun getProjectIndexableFiles(project: Project): IdFilter? {
    if (!UnindexedFilesScanner.isProjectContentFullyScanned(project) || UnindexedFilesUpdater.isIndexUpdateInProgress(project)) {
      return null
    }
    return getFilter(project)
  }

  override fun resetFileIds(project: Project) {
    assert(UnindexedFilesUpdater.isIndexUpdateInProgress(project))

    getFilter(project)?.resetFileIds()
  }

  private fun getFilter(project: Project) = myProjectFilters[project]

  override fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): List<Project> {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    return myProjectFilters.mapNotNullTo(SmartList()) { (p, filter) ->
      val fileIsInProject = filter.ensureFileIdPresent(fileId) {
        matchedProjects.contains(p)
      }
      if (fileIsInProject) p else null
    }
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

  override fun findProjectsForFile(fileId: Int): Set<Project> {
    val projects = SmartHashSet<Project>()
    for ((project, filter) in myProjectFilters) {
      if (filter.containsFileId(fileId)) {
        projects.add(project)
      }
    }
    return projects
  }

  override fun runHealthCheck() {
    if (!IndexInfrastructure.hasIndices()) return

    try {
      for ((project, filter) in myProjectFilters) {
        var errors: List<HealthCheckError> = emptyList()
        ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
          if (DumbService.isDumb(project)) return@runInReadActionWithWriteActionPriority
          errors = filter.runHealthCheck(project)
        }

        if (errors.isEmpty()) continue

        for (error in errors) {
          error.fix()
        }

        val errorsToReport = errors
          .filter { it.shouldBeReported }

        if (errors.size > errorsToReport.size) {
          FileBasedIndexImpl.LOG.info("${errors.size - errorsToReport.size} of ${filter.javaClass.simpleName} health check errors were filtered out")
        }

        if (errorsToReport.isEmpty()) return

        val summary = errorsToReport
          .groupBy { it::class.java }
          .entries.joinToString("\n") { (clazz, e) ->
            "${e.size} ${clazz.simpleName} errors. Examples:\n" + e.take(10).joinToString("\n") { error ->
              ReadAction.nonBlocking(Callable { error.presentableText }).executeSynchronously()
            }
          }

        FileBasedIndexImpl.LOG.warn("${filter.javaClass.simpleName} health check found ${errorsToReport.size} errors in project ${project.name}:\n$summary")
      }
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
  }
}