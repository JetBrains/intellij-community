// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

private class RepositoryLibrarySynchronizer : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      val trackedQueue = project.serviceAsync<TrackedLibrarySynchronizationQueue>()
      trackedQueue.subscribeToModelUpdates()
      trackedQueue.loadDependencies()
      return
    }

    val disposable = project.serviceAsync<RemoteRepositoriesConfiguration>()
    val synchronizationQueue = project.serviceAsync<LibraryIdSynchronizationQueue>()
    val synchronizer = ChangedRepositoryLibraryIdSynchronizer(synchronizationQueue)
    val globalLibSynchronizer = GlobalChangedRepositoryLibraryIdSynchronizer(synchronizationQueue, disposable)
    for (libraryTable in getGlobalAndCustomLibraryTables()) {
      libraryTable.addListener(globalLibSynchronizer, disposable)
    }
    installOnExistingLibraries(globalLibSynchronizer, disposable)
    project.messageBus.connect(disposable).subscribe(WorkspaceModelTopics.CHANGED, synchronizer)
    synchronizationQueue.requestAllLibrariesSynchronization()
  }
}

internal suspend fun collectLibraries(project: Project, predicate: (Library) -> Boolean): Set<Library> {
  val moduleManager = project.serviceAsync<ModuleManager>()
  val projectLibraryTable = project.serviceAsync<ProjectLibraryTable>()
  return readAction {
    doCollectLibraries(moduleManager = moduleManager, projectLibraryTable = projectLibraryTable, predicate = predicate)
  }
}

private fun doCollectLibraries(
  moduleManager: ModuleManager,
  projectLibraryTable: ProjectLibraryTable,
  predicate: (Library) -> Boolean,
): Set<Library> {
  val result = LinkedHashSet<Library>()
  for (module in moduleManager.modules) {
    OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary { library: Library ->
      if (predicate(library)) {
        result.add(library)
      }
      true
    }
  }
  for (library in projectLibraryTable.libraries) {
    if (predicate(library)) {
      result.add(library)
    }
  }
  return result
}

internal fun isLibraryHasFixedVersion(properties: RepositoryLibraryProperties): Boolean {
  val version = properties.version ?: return false
  return version != RepositoryLibraryDescription.LatestVersionId &&
         version != RepositoryLibraryDescription.ReleaseVersionId &&
         !version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)
}

internal fun isLibraryNeedToBeReloaded(library: LibraryEx, properties: RepositoryLibraryProperties): Boolean {
  return !isLibraryHasFixedVersion(properties) ||
         OrderRootType.getAllTypes().any { library.getFiles(it).size != library.getUrls(it).size }
}

internal suspend fun collectLibrariesToSync(project: Project): Set<Library> {
  return collectLibraries(project) { library ->
    library is LibraryEx &&
    isLibraryNeedToBeReloaded(library, library.properties as? RepositoryLibraryProperties ?: return@collectLibraries false)
  }
}

suspend fun syncLibraries(project: Project) {
  val toSync = collectLibrariesToSync(project)
  withContext(Dispatchers.EDT) {
    for (library in toSync) {
      if (LibraryTableImplUtil.isValidLibrary(library)) {
        RepositoryUtils.reloadDependencies(project, (library as LibraryEx))
      }
    }
  }
}