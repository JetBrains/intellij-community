// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SmartList
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.indexing.isFirstProjectScanningPerformed
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

fun useCachingFilesFilter() = Registry.`is`("caching.index.files.filter.enabled")
fun usePersistentFilesFilter() = Registry.`is`("persistent.index.files.filter.enabled")
fun allowABTest() = Registry.`is`("persistent.index.filter.allow.ab.test")

internal sealed interface ProjectIndexableFilesFilterHolder {
  fun getProjectIndexableFiles(project: Project): ProjectIndexableFilesFilter?

  /**
   * @returns true if fileId already contained in or was added to one of project filters
   */
  fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): List<Project>

  fun addFileId(fileId: Int, project: Project)

  fun resetFileIds(project: Project)

  fun removeFile(fileId: Int)

  fun findProjectForFile(fileId: Int): Project?

  fun findProjectsForFile(fileId: Int): List<Project>

  fun onProjectClosing(project: Project, vfsCreationTimestamp: Long)

  fun onProjectOpened(project: Project, vfsCreationTimestamp: Long)

  /**
   * This is a temp method
   */
  fun wasDataLoadedFromDisk(project: Project): Boolean
}

private val log = logger<IncrementalProjectIndexableFilesFilterHolder>()

internal class IncrementalProjectIndexableFilesFilterHolder : ProjectIndexableFilesFilterHolder {
  private val myProjectFilters: ConcurrentMap<Project, ProjectIndexableFilesFilter> = ConcurrentHashMap()

  override fun onProjectClosing(project: Project, vfsCreationTimestamp: Long) {
    val pair = myProjectFilters.remove(project)
    pair?.onProjectClosing(project, vfsCreationTimestamp)
  }

  override fun onProjectOpened(project: Project, vfsCreationTimestamp: Long) {
    val factory = chooseFactory(project.name)
    myProjectFilters[project] = factory.create(project, vfsCreationTimestamp)
  }

  private fun chooseFactory(projectName: String): ProjectIndexableFilesFilterFactory {
    if (usePersistentFilesFilter() && allowABTest()) {
        val deviceId = log.runAndLogException {
          DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "FUS")
        }
        if (deviceId != null) {
          val rawHash = deviceId.hashCode()
          val chosenFactory = if (rawHash % 2 == 0) PersistentProjectIndexableFilesFilterFactory() else IncrementalProjectIndexableFilesFilterFactory()
          log.info("${chosenFactory.javaClass.simpleName} is chosen as indexable files filter factory for project: $projectName. Device hash is ${rawHash} % 2 = ${rawHash % 2}")
          return chosenFactory
        }
    }

    val factory = if (usePersistentFilesFilter()) PersistentProjectIndexableFilesFilterFactory()
    else if (useCachingFilesFilter()) CachingProjectIndexableFilesFilterFactory()
    else IncrementalProjectIndexableFilesFilterFactory()

    log.info("${factory.javaClass.simpleName} is chosen as indexable files filter factory for project: $projectName")
    return factory
  }

  override fun wasDataLoadedFromDisk(project: Project): Boolean {
    return myProjectFilters[project]?.wasDataLoadedFromDisk ?: false
  }

  override fun getProjectIndexableFiles(project: Project): ProjectIndexableFilesFilter? {
    if (!isFirstProjectScanningPerformed(project) || UnindexedFilesUpdater.isScanningInProgress(project)) {
      return null
    }
    return getFilter(project)
  }

  override fun resetFileIds(project: Project) {
    assert(UnindexedFilesUpdater.isScanningInProgress(project))

    getFilter(project)?.resetFileIds()
  }

  private fun getFilter(project: Project) = myProjectFilters[project]

  override fun ensureFileIdPresent(fileId: Int, projects: () -> Set<Project>): List<Project> {
    val matchedProjects by lazy(LazyThreadSafetyMode.NONE) { projects() }
    return myProjectFilters.mapNotNullTo(SmartList()) { (p, pair) ->
      val fileIsInProject = pair.ensureFileIdPresent(fileId) {
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

  override fun findProjectsForFile(fileId: Int): List<Project> {
    val projects = SmartList<Project>()
    for ((project, filter) in myProjectFilters) {
      if (filter.containsFileId(fileId)) {
        projects.add(project)
      }
    }
    return projects
  }
}