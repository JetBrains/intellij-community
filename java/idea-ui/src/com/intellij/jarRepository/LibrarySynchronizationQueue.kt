// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.jarRepository.RepositoryLibrarySynchronizer.removeDuplicatedUrlsFromRepositoryLibraries
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

@Service(Service.Level.PROJECT)
internal class LibrarySynchronizationQueue(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibrarySynchronizationQueue = project.service()
  }

  private val queue = LinkedHashSet<LibraryEx>()

  fun requestAllLibrariesSynchronization() {
    scope.launch(Dispatchers.IO) {
      removeDuplicatedUrlsFromRepositoryLibraries(project)
      val libraries = RepositoryLibrarySynchronizer.collectLibrariesToSync(project)
      coroutineScope {
        for (library in libraries) {
          requestSynchronization(library as LibraryEx)
        }
      }
      flush()
    }
  }

  fun requestSynchronization(library: LibraryEx) {
    scope.launch {
      if (library.needToReload()) {
        synchronized(queue) {
          queue.add(library)
        }
      }
    }
  }

  fun revokeSynchronization(library: LibraryEx) {
    synchronized(queue) {
      queue.remove(library)
    }
  }

  fun flush() {
    scope.launch(Dispatchers.IO) {
      while (isActive) {
        if (project.isDisposed) break

        val library = nextLibrary()
        if (library == null) break

        if (library.needToReload()) {
          RepositoryUtils.reloadDependencies(project, library)
        }
      }
    }
  }

  private fun LibraryEx.needToReload(): Boolean {
    val props = properties as? RepositoryLibraryProperties ?: return false

    val isValid = runReadAction { LibraryTableImplUtil.isValidLibrary(this) }
    val needToReload = RepositoryLibrarySynchronizer.isLibraryNeedToBeReloaded(this, props)
    return isValid && needToReload
  }

  private fun nextLibrary(): LibraryEx? = synchronized(queue) {
    val library = queue.firstOrNull() ?: return null
    queue.remove(library)
    return library
  }
}