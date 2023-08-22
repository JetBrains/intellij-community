// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.jarRepository.RepositoryLibrarySynchronizer.removeDuplicatedUrlsFromRepositoryLibraries
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import kotlin.coroutines.coroutineContext

private val LOG = logger<LibrarySynchronizationQueue>()

@Service(Service.Level.PROJECT)
internal class LibrarySynchronizationQueue(private val project: Project, private val scope: CoroutineScope) {
  private val synchronizationRequests = Channel<Request>(capacity = Channel.UNLIMITED).apply {
    scope.coroutineContext.job.invokeOnCompletion {
      close()
    }
  }

  private val toSynchronize = mutableSetOf<LibraryEx>()

  init {
    scope.launch(Dispatchers.IO) {
      for (request in synchronizationRequests) {
        try {
          when (request) {
            is Request.QueueSynchronization -> {
              toSynchronize.add(request.library)
            }
            is Request.RevokeSynchronization -> {
              toSynchronize.remove(request.library)
            }
            Request.Flush -> {
              synchronizeLibraries(toSynchronize)
              toSynchronize.clear()
            }
            Request.AllLibrariesSynchronization -> {
              val newLibrariesToSync = readAction {
                removeDuplicatedUrlsFromRepositoryLibraries(project)
                RepositoryLibrarySynchronizer.collectLibrariesToSync(project)
              }
              toSynchronize.addAll(newLibrariesToSync.map { lib -> lib as LibraryEx })
              synchronizeLibraries(toSynchronize)
              toSynchronize.clear()
            }
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          // continue collecting on exceptions
          LOG.warn(e)
        }
      }
    }
  }

  fun requestAllLibrariesSynchronization() {
    synchronizationRequests.trySend(Request.AllLibrariesSynchronization)
  }

  fun requestSynchronization(library: LibraryEx) {
    synchronizationRequests.trySend(Request.QueueSynchronization(library))
  }

  fun revokeSynchronization(library: LibraryEx) {
    synchronizationRequests.trySend(Request.RevokeSynchronization(library))
  }

  fun flush() {
    synchronizationRequests.trySend(Request.Flush)
  }

  private suspend fun synchronizeLibraries(libraries: Collection<LibraryEx>) {
    for (library in libraries) {
      if (!coroutineContext.isActive) {
        return
      }
      val shouldReload = readAction { library.needToReload() }

      if (shouldReload) {
        RepositoryUtils.reloadDependencies(project, library)
      }
    }
  }

  private sealed interface Request {
    class QueueSynchronization(val library: LibraryEx) : Request
    class RevokeSynchronization(val library: LibraryEx) : Request
    object AllLibrariesSynchronization : Request
    object Flush : Request
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibrarySynchronizationQueue = project.service()
  }
}

internal fun LibraryEx.needToReload(): Boolean {
  val props = properties as? RepositoryLibraryProperties ?: return false

  val isValid = runReadAction { LibraryTableImplUtil.isValidLibrary(this) }
  val needToReload = RepositoryLibrarySynchronizer.isLibraryNeedToBeReloaded(this, props)
  return isValid && needToReload
}