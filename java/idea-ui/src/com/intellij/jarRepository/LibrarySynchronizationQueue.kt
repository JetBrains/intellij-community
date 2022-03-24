// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.jarRepository.RepositoryLibrarySynchronizer.removeDuplicatedUrlsFromRepositoryLibraries
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.util.concurrency.SequentialTaskExecutor
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

internal class LibrarySynchronizationQueue(private val project: Project) {
  private companion object {
    val EXECUTOR = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("RemoteLibrarySynchronizerQueue")
  }

  private val queue = LinkedHashSet<LibraryEx>()

  fun synchronizeAllLibraries() = EXECUTOR.execute {
    removeDuplicatedUrlsFromRepositoryLibraries(project)
    val libraries = RepositoryLibrarySynchronizer.collectLibrariesToSync(project)
    for (library in libraries) {
      requestSynchronization(library as LibraryEx)
    }
    flush()
  }

  fun requestSynchronization(library: LibraryEx) {
    synchronized(queue) {
      queue.add(library)
    }
  }

  fun revokeSynchronization(library: LibraryEx) {
    synchronized(queue) {
      queue.remove(library)
    }
  }

  fun flush() = EXECUTOR.execute {
    while (true) {
      if (project.isDisposed) break

      val library = nextLibrary()
      if (library == null) break

      val props = library.properties as? RepositoryLibraryProperties ?: continue

      val isValid = runReadAction { LibraryTableImplUtil.isValidLibrary(library) }
      val needToReload = RepositoryLibrarySynchronizer.isLibraryNeedToBeReloaded(library, props)
      if (isValid && needToReload) {
        RepositoryUtils.reloadDependencies(project, library)
      }
    }
  }

  private fun nextLibrary(): LibraryEx? = synchronized(queue) {
    val library = queue.firstOrNull() ?: return null
    queue.remove(library)
    return library
  }
}