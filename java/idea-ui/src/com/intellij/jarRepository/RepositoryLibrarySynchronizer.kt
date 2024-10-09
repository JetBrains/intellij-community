// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.ide.JavaUiBundle
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

private class RepositoryLibrarySynchronizer : ProjectActivity {
  private object LoadDependenciesActivityKey : ActivityKey {
    override val presentableName: String
      get() = "download-jars"
  }

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      project.trackActivity(LoadDependenciesActivityKey) {
        val libs = collectLibrariesToSync(project)
        if (!libs.isEmpty()) {
          loadDependenciesSyncImpl(project, libs)}
      }
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

internal fun collectLibraries(project: Project, predicate: (Library) -> Boolean): Set<Library> {
  val result = LinkedHashSet<Library>()
  ApplicationManager.getApplication().runReadAction {
    if (project.isDisposed) {
      return@runReadAction
    }
    for (module in ModuleManager.getInstance(project).modules) {
      OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary { library: Library ->
        if (predicate(library)) {
          result.add(library)
        }
        true
      }
    }
    for (library in LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries) {
      if (predicate(library)) {
        result.add(library)
      }
    }
  }
  return result
}

internal fun isLibraryNeedToBeReloaded(library: LibraryEx, properties: RepositoryLibraryProperties): Boolean {
  val version = properties.version ?: return false
  if (version == RepositoryLibraryDescription.LatestVersionId ||
      version == RepositoryLibraryDescription.ReleaseVersionId ||
      version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) {
    return true
  }

  return OrderRootType.getAllTypes().any { library.getFiles(it).size != library.getUrls(it).size }
}

internal fun removeDuplicatedUrlsFromRepositoryLibraries(project: Project) {
  val libraries = collectLibraries(project) {
    it is LibraryEx && it.properties is RepositoryLibraryProperties && hasDuplicatedRoots(it)
  }

  if (libraries.isEmpty()) {
    return
  }

  ApplicationManager.getApplication().invokeLater(
    {
      val validLibraries = libraries.filter { LibraryTableImplUtil.isValidLibrary(it) }
      if (validLibraries.isEmpty()) {
        return@invokeLater
      }

      ApplicationManager.getApplication().runWriteAction {
        for (library in validLibraries) {
          val model = library.modifiableModel
          for (type in OrderRootType.getAllTypes()) {
            val urls = model.getUrls(type!!)
            val uniqueUrls = ObjectLinkedOpenHashSet(urls)
            if (uniqueUrls.size != urls.size) {
              for (url in urls) {
                model.removeRoot(url, type)
              }
              for (url in uniqueUrls) {
                model.addRoot(url, type)
              }
            }
          }
          model.commit()
        }
      }
      val libraryText = if (validLibraries.size == 1) {
        "'${validLibraries.iterator().next().presentableName}' library"
      }
      else {
        "${validLibraries.size} libraries"
      }
      Notifications.Bus.notify(JarRepositoryManager.getNotificationGroup().createNotification(
        JavaUiBundle.message("notification.title.repository.libraries.cleanup"),
        JavaUiBundle.message("notification.text.duplicated.urls.were.removed",
                             libraryText,
                             ApplicationNamesInfo.getInstance().fullProductName),
        NotificationType.INFORMATION
      ), project)
    },
    project.disposed,
  )
}

internal fun collectLibrariesToSync(project: Project): Set<Library> {
  return collectLibraries(project) { library ->
    library is LibraryEx &&
    isLibraryNeedToBeReloaded(library, library.properties as? RepositoryLibraryProperties ?: return@collectLibraries false)
  }
}

private fun hasDuplicatedRoots(library: Library): Boolean {
  for (type in OrderRootType.getAllTypes()) {
    val urls = library.getUrls(type)
    @Suppress("SSBasedInspection")
    if (urls.size != ObjectOpenHashSet(urls).size) {
      return true
    }
  }
  return false
}

fun syncLibraries(project: Project) {
  val toSync = collectLibrariesToSync(project)
  ApplicationManager.getApplication().invokeLater({
                                                    for (library in toSync) {
                                                      if (LibraryTableImplUtil.isValidLibrary(library)) {
                                                        RepositoryUtils.reloadDependencies(project, (library as LibraryEx))
                                                      }
                                                    }
                                                  }, project.disposed)
}