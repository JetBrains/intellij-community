// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.artifacts

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.jvm.models.CustomRootDataLoader
import com.intellij.platform.ml.embeddings.jvm.wrappers.AbstractEmbeddingsStorageWrapper.Companion.OLD_API_DIR_NAME
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.Decompressor
import com.intellij.util.io.delete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/* Service that manages the artifacts for local semantic models */
@OptIn(ExperimentalCoroutinesApi::class)
@Service
class KInferenceLocalArtifactsManager {
  private val root = File(PathManager.getSystemPath())
    .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
    .resolve(OLD_API_DIR_NAME)
    .also { Files.createDirectories(it.toPath()) }

  private val modelArtifactsRoot = root.resolve(MODEL_ARTIFACTS_DIR)

  private val downloadContext = Dispatchers.IO.limitedParallelism(1)
  private var failNotificationShown = false
  private var downloadCanceled = false

  init {
    root.toPath().listDirectoryEntries().filter { it.name != MODEL_ARTIFACTS_DIR }.forEach { it.delete(recursively = true) }
  }

  fun getCustomRootDataLoader(): CustomRootDataLoader = CustomRootDataLoader(modelArtifactsRoot.toPath())

  @RequiresBackgroundThread
  suspend fun downloadArtifactsIfNecessary(project: Project? = null,
                                           retryIfCanceled: Boolean = true) {
    withContext(downloadContext) {
      if (!checkArtifactsPresent() && !ApplicationManager.getApplication().isUnitTestMode && (retryIfCanceled || !downloadCanceled)) {
        logger.debug("Semantic search artifacts are not present, starting the download...")
        if (project != null) {
          withBackgroundProgress(project, ARTIFACTS_DOWNLOAD_TASK_NAME) {
            try {
              coroutineToIndicator { // platform code relies on the existence of indicator
                downloadArtifacts()
              }
            }
            catch (e: CancellationException) {
              logger.debug("Artifacts downloading was canceled")
              downloadCanceled = true
              throw e
            }
          }
        }
        else {
          downloadArtifacts()
        }
      }
    }
  }

  fun checkArtifactsPresent(): Boolean {
    return Files.isDirectory(modelArtifactsRoot.toPath()) && modelArtifactsRoot.toPath().listDirectoryEntries().isNotEmpty()
  }

  private fun downloadArtifacts() {
    Files.createDirectories(root.toPath())
    try {
      DownloadableFileService.getInstance().run {
        createDownloader(listOf(createFileDescription(MAVEN_ROOT, ARCHIVE_NAME)), ARTIFACTS_DOWNLOAD_TASK_NAME)
      }.download(root)
      logger.debug { "Downloaded archive with search artifacts into ${root.absoluteFile}" }

      modelArtifactsRoot.deleteRecursively()
      unpackArtifactsArchive(root.resolve(ARCHIVE_NAME), root)
      logger.debug { "Extracted model artifacts into the ${root.absoluteFile}" }
    }
    catch (e: IOException) {
      logger.warn("Failed to download semantic search artifacts: $e")
      if (!failNotificationShown) {
        showDownloadErrorNotification()
        failNotificationShown = true
      }
    }
  }

  private fun unpackArtifactsArchive(archiveFile: File, destination: File) {
    Decompressor.Zip(archiveFile).overwrite(false).extract(destination)
    archiveFile.delete()
  }

  companion object {
    const val SEMANTIC_SEARCH_RESOURCES_DIR: String = "semantic-search"

    private val ARTIFACTS_DOWNLOAD_TASK_NAME
      get() = EmbeddingsBundle.getMessage("ml.embeddings.artifacts.download.name")
    private val MODEL_VERSION
      get() = Registry.stringValue("intellij.platform.ml.embeddings.ki.model.version")
    private val MAVEN_ROOT
      get() = Registry.stringValue("intellij.platform.ml.embeddings.model.artifacts.link").replace("%MODEL_VERSION%", MODEL_VERSION)

    private const val MODEL_ARTIFACTS_DIR = "models"
    private const val ARCHIVE_NAME = "semantic-text-search.jar"
    private const val NOTIFICATION_GROUP_ID = "Embedding-based search"

    private val logger = Logger.getInstance(KInferenceLocalArtifactsManager::class.java)

    fun getInstance(): KInferenceLocalArtifactsManager = service()

    private fun showDownloadErrorNotification() {
      NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        ?.createNotification(
          EmbeddingsBundle.getMessage("ml.embeddings.notification.model.downloading.failed.title"),
          EmbeddingsBundle.getMessage("ml.embeddings.notification.model.downloading.failed.content"),
          NotificationType.WARNING
        )
        ?.notify(null)
    }
  }
}