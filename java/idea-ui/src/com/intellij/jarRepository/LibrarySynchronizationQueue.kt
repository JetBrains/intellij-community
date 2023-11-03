// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.util.Computable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import kotlin.coroutines.coroutineContext

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
                collectLibrariesToSync(project)
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
          thisLogger().warn(e)
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
      if (readAction { library.needToReload() }) {
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
    fun getInstance(project: Project): LibrarySynchronizationQueue = project.service()
  }
}

internal fun LibraryEx.needToReload(): Boolean {
  val props = properties as? RepositoryLibraryProperties ?: return false
  val isValid = ApplicationManager.getApplication().runReadAction (Computable { LibraryTableImplUtil.isValidLibrary(this) })
  val needToReload = isLibraryNeedToBeReloaded(library = this, properties = props)
  return isValid && needToReload
}