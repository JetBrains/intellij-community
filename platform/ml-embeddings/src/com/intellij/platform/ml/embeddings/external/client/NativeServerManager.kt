// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
class NativeServerManager: Disposable {
  private var connection: NativeServerConnection? = null
  private val mutex = Mutex()

  suspend fun getConnection(): NativeServerConnection = mutex.withLock {
    if (connection == null) {
      val artifactsManager = LocalArtifactsManager.getInstance()
      val projectForIndicator = ProjectUtil.getActiveProject() ?: ProjectUtil.getOpenProjects().firstOrNull()
      artifactsManager.downloadArtifactsIfNecessary(projectForIndicator, retryIfCanceled = true)
      val modelArtifact = artifactsManager.getModelArtifact()
      val serverArtifact = artifactsManager.getServerArtifact()

      val startupArguments = NativeServerStartupArguments(
        modelPath = modelArtifact.getWeightsPath(),
        vocabPath = modelArtifact.getVocabPath(),
        storageRoot = LocalArtifactsManager.indicesRoot
      )

      connection = NativeServerConnection.create(serverArtifact.getBinaryPath(), startupArguments, 60.seconds) {
        println("Cannot start native embeddings server")
      }
    }
    connection!!
  }

  private suspend fun shutdown() = mutex.withLock {
    connection?.shutdown()
  }

  override fun dispose() {
    runBlockingMaybeCancellable { shutdown() }
  }

  companion object {
    fun getInstance(): NativeServerManager = service()
  }
}