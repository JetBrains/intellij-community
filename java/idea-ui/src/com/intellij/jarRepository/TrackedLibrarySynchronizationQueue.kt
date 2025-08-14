// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
  private val workspaceSubscription = scope.launch(start = CoroutineStart.LAZY) {
    project.serviceAsync<WorkspaceModel>().eventLog.collect { event ->
      project.trackActivity(LoadDependenciesActivityKey) {
        val libraryChanges = event.getChanges(LibraryEntity::class.java)
        val libraryPropertiesChanges = event.getChanges(LibraryPropertiesEntity::class.java)
        if (libraryChanges.isNotEmpty() || libraryPropertiesChanges.isNotEmpty()) {
          requestDependenciesSync()
        }
      }
    }
  }

  init {
    launchLoadChannelConsumer()
  }

  /**
   * Do not wait for completion of loading deps, but keeps LoadDependenciesActivityKey active until all libraries are loaded.
   */
  suspend fun loadDependencies() {
    project.trackActivity(LoadDependenciesActivityKey) {
      requestDependenciesSync()
    }
  }

  fun subscribeToModelUpdates() {
    workspaceSubscription.start()
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

  private fun requestDependenciesSync() {
    scope.launchTracked {
      val deferred = CompletableDeferred<Unit>()
      if (channel.trySend(deferred).isSuccess) {
        deferred.await()
      }
    }
  }
}