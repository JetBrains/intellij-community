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
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
internal class LibrarySynchronizationQueue(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibrarySynchronizationQueue = project.service()
    private val log = logger<LibrarySynchronizationQueue>()
  }

  private val synchronizationRequests = MutableSharedFlow<Request>(extraBufferCapacity = Int.MAX_VALUE)
  private val toSynchronize = mutableSetOf<LibraryEx>()

  init {
    scope.launch(Dispatchers.IO) {
      synchronizationRequests.collect {
        try {
          when (it) {
            is Request.QueueSynchronization -> {
              toSynchronize.add(it.library)
            }
            is Request.RevokeSynchronization -> {
              toSynchronize.remove(it.library)
            }
            is Request.Flush -> {
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
          log.warn(e)
        }
      }
    }
  }

  fun requestAllLibrariesSynchronization() {
    synchronizationRequests.tryEmit(Request.Flush)
  }

  fun requestSynchronization(library: LibraryEx) {
    synchronizationRequests.tryEmit(Request.QueueSynchronization(library))
  }

  fun revokeSynchronization(library: LibraryEx) {
    synchronizationRequests.tryEmit(Request.RevokeSynchronization(library))
  }

  fun flush() {
    synchronizationRequests.tryEmit(Request.Flush)
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
    object Flush : Request
  }
}

internal fun LibraryEx.needToReload(): Boolean {
  val props = properties as? RepositoryLibraryProperties ?: return false

  val isValid = runReadAction { LibraryTableImplUtil.isValidLibrary(this) }
  val needToReload = RepositoryLibrarySynchronizer.isLibraryNeedToBeReloaded(this, props)
  return isValid && needToReload
}