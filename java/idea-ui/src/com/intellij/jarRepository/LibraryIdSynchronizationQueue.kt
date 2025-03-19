// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
class LibraryIdSynchronizationQueue(private val project: Project, private val scope: CoroutineScope) {
  private val synchronizationRequests = Channel<Request>(capacity = Channel.UNLIMITED).apply {
    scope.coroutineContext.job.invokeOnCompletion {
      close()
    }
  }

  private val toSynchronize = mutableSetOf<LibraryId>()

  init {
    scope.launch(Dispatchers.Default) {
      for (request in synchronizationRequests) {
        try {
          when (request) {
            is Request.QueueSynchronization -> {
              toSynchronize.add(request.libraryId)
            }
            is Request.RevokeSynchronization -> {
              toSynchronize.remove(request.libraryId)
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
              toSynchronize.addAll(newLibrariesToSync.map { lib -> (lib as LibraryBridge).libraryId })
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

  fun requestSynchronization(libraryId: LibraryId) {
    synchronizationRequests.trySend(Request.QueueSynchronization(libraryId))
  }

  fun revokeSynchronization(libraryId: LibraryId) {
    synchronizationRequests.trySend(Request.RevokeSynchronization(libraryId))
  }

  fun flush() {
    synchronizationRequests.trySend(Request.Flush)
  }

  private suspend fun synchronizeLibraries(libraryIds: Collection<LibraryId>) {
    val currentSnapshot = WorkspaceModel.getInstance(project).currentSnapshot
    for (libraryId in libraryIds) {
      if (!coroutineContext.isActive) {
        return
      }
      val libraryBridge = libraryId.resolve(currentSnapshot)?.let {
        currentSnapshot.libraryMap.getDataByEntity(it)
      } ?: continue
      val libraryEx = libraryBridge as? LibraryEx ?: continue

      val needToReload = readAction {
        if (libraryEx.isDisposed) return@readAction false
        libraryEx.needToReload()
      }
      if (needToReload) {
        RepositoryUtils.reloadDependencies(project, libraryEx)
      }
    }
  }

  private sealed interface Request {
    class QueueSynchronization(val libraryId: LibraryId) : Request
    class RevokeSynchronization(val libraryId: LibraryId) : Request
    data object AllLibrariesSynchronization : Request
    data object Flush : Request
  }

  companion object {
    fun getInstance(project: Project): LibraryIdSynchronizationQueue = project.service()
  }
}

internal fun LibraryEx.needToReload(): Boolean {
  val props = properties as? RepositoryLibraryProperties ?: return false
  val isValid = ApplicationManager.getApplication().runReadAction(Computable { LibraryTableImplUtil.isValidLibrary(this) })
  val needToReload = isLibraryNeedToBeReloaded(library = this, properties = props)
  return isValid && needToReload
}