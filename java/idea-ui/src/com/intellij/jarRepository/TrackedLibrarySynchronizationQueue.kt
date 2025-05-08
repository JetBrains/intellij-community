// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Track loading libraries with ActivityKey.
 * Activity is in progress while loading, or there is a request to load libraries.
 * Trying to download all unavailable libraries, runs only one download activity at a time.
 */
@Service(Service.Level.PROJECT)
internal class TrackedLibrarySynchronizationQueue(val project: Project, val scope: CoroutineScope) {
  private object LoadDependenciesActivityKey : ActivityKey {
    override val presentableName: String
      get() = "download-jars"
  }

  private val channel = Channel<CompletableDeferred<Unit>>(1)
  private val subscribedToUpdates = AtomicBoolean(false)

  init {
    launchLoadChannelConsumer()
  }

  suspend fun loadDependencies() {
    project.trackActivity(LoadDependenciesActivityKey) {
      requestAndWaitDependenciesSync()
    }
  }

  fun subscribeToModelUpdates() {
    if (!subscribedToUpdates.compareAndSet(false, true)) return
    scope.launch {
      project.serviceAsync<WorkspaceModel>().eventLog.collect { event ->
        project.trackActivity(LoadDependenciesActivityKey) {
          val libraryChanges = event.getChanges(LibraryEntity::class.java)
          val libraryPropertiesChanges = event.getChanges(LibraryPropertiesEntity::class.java)
          if (libraryChanges.isNotEmpty() || libraryPropertiesChanges.isNotEmpty()) requestAndWaitDependenciesSync()
        }
      }
    }
  }

  private fun launchLoadChannelConsumer() {
    scope.launch {
      for (deferred in channel) {
        try {
          val libs = collectLibrariesToSync(project)
          if (!libs.isEmpty()) {
            loadDependenciesSyncImpl(project, libs)
          }
        }
        finally {
          deferred.complete(Unit)
        }
      }
    }
  }

  private suspend fun requestAndWaitDependenciesSync() {
    val deferred = CompletableDeferred<Unit>()
    if (!channel.trySend(deferred).isSuccess) return
    deferred.await()
  }
}