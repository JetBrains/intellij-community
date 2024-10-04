// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.artifacts

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
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.external.client.UnsupportedArchitectureException
import com.intellij.platform.ml.embeddings.external.client.UnsupportedOSException
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.MODEL_VERSION
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.SERVER_VERSION
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.getArchitectureId
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.getOsId
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer.Companion.INDEXING_VERSION
import com.intellij.platform.ml.embeddings.jvm.models.CustomRootDataLoader
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.delete
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/* Service that manages the artifacts for local embedding search models */
@Service
class LocalArtifactsManager {
  init {
    clearUnusedResources()
    removeOutdatedModels()
    removeOutdatedServers()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val downloadContext = Dispatchers.IO.limitedParallelism(1)

  private var failNotificationShown = false
  private var downloadCanceled = false

  // TODO: the list may be changed depending on the project size
  private val availableModels = listOf(ModelArtifact.SmallModelArtifact)

  fun getCustomRootDataLoader() = CustomRootDataLoader(modelsRoot)

  @RequiresBackgroundThread
  suspend fun downloadArtifactsIfNecessary(
    project: Project? = null,
    retryIfCanceled: Boolean = true,
  ) {
    if (!retryIfCanceled && downloadCanceled) return

    withContext(downloadContext) {
      val absentArtifacts: MutableList<DownloadableArtifact> = availableModels.filter { !it.checkPresent() }.toMutableList()
      if (!NativeServerArtifact.checkPresent()) absentArtifacts.add(NativeServerArtifact)
      if (absentArtifacts.isNotEmpty()) {
        logger.debug("Embedding search artifacts are not present, starting the download...")
        if (project != null) {
          withBackgroundProgress(project, ARTIFACTS_DOWNLOAD_TASK_NAME) {
            try {
              coroutineToIndicator { // platform code relies on the existence of indicator
                downloadArtifacts(absentArtifacts)
              }
            }
            catch (e: CancellationException) {
              logger.debug("Artifacts downloading was canceled")
              downloadCanceled = true
              throw e
            }
          }
        }
        else downloadArtifacts(absentArtifacts)
      }
    }
  }

  // TODO: provide model id in arguments
  fun getModelArtifact(): ModelArtifact = availableModels.first()
  fun getServerArtifact() = NativeServerArtifact

  fun checkArtifactsPresent(): Boolean = availableModels.all { it.checkPresent() }

  private fun downloadArtifacts(artifacts: List<DownloadableArtifact>) {
    try {
      DownloadableFileService.getInstance().run {
        val filesToDownload = artifacts.map { createFileDescription(it.downloadLink, it.archiveName) }
        createDownloader(filesToDownload, ARTIFACTS_DOWNLOAD_TASK_NAME)
      }.download(resourcesRoot.toFile())
      logger.debug { "Downloaded embedding search artifacts into ${resourcesRoot.absolute()}" }

      for (artifact in artifacts) {
        val destination = artifact.destination
        destination.delete(recursively = true)
        unpackArtifactsArchive(resourcesRoot.resolve(artifact.archiveName), destination)
      }
      logger.debug { "Extracted embedding search artifacts into the ${resourcesRoot.absolute()}" }
    }
    catch (e: IOException) {
      logger.warn("Failed to download semantic search artifacts: $e")
      if (!failNotificationShown) {
        showDownloadErrorNotification()
        failNotificationShown = true
      }
    }
  }

  private fun unpackArtifactsArchive(archiveFile: Path, destination: Path) {
    ZipUtil.extract(archiveFile, destination, null, true)
    archiveFile.delete()
  }

  private fun removeOutdatedModels() = removeSiblingsOf(modelsRoot)
  private fun removeOutdatedServers() = removeSiblingsOf(serverRoot)
  private fun removeSiblingsOf(path: Path) {
    path.parent.listDirectoryEntries().filter { it.name != path.name }.forEach { it.delete(recursively = true) }
  }

  private fun clearUnusedResources() {
    resourcesRoot.listDirectoryEntries().filter {
      it.name !in listOf(INDICES_DIR_NAME, MODELS_DIR_NAME, SERVER_DIR_NAME)
    }.forEach { it.delete(recursively = true) }
  }

  companion object {
    const val SEMANTIC_SEARCH_RESOURCES_DIR_NAME = "semantic-search" // TODO: move to common constants

    const val INDICES_DIR_NAME = "indices"
    private const val MODELS_DIR_NAME = "models"
    private const val SERVER_DIR_NAME = "server"

    private val ARTIFACTS_DOWNLOAD_TASK_NAME
      get() = EmbeddingsBundle.getMessage("ml.embeddings.artifacts.download.name")
    internal val MODEL_VERSION
      get() = Registry.stringValue("intellij.platform.ml.embeddings.model.version")
    internal val SERVER_VERSION
      get() = Registry.stringValue("intellij.platform.ml.embeddings.server.version")

    private val resourcesRoot = Path(PathManager.getSystemPath()) / SEMANTIC_SEARCH_RESOURCES_DIR_NAME
    internal val serverRoot: Path = (resourcesRoot / SERVER_DIR_NAME / SERVER_VERSION).also { Files.createDirectories(it) }
    internal val modelsRoot: Path = (resourcesRoot / MODELS_DIR_NAME / MODEL_VERSION).also { Files.createDirectories(it) }
    internal val indicesRoot: Path = (resourcesRoot / INDICES_DIR_NAME / INDEXING_VERSION).also { Files.createDirectories(it) }

    private const val NOTIFICATION_GROUP_ID = "Embedding-based search"

    private val logger = Logger.getInstance(LocalArtifactsManager::class.java)

    fun getInstance(): LocalArtifactsManager = service()

    private fun showDownloadErrorNotification() {
      if (ApplicationManager.getApplication().isInternal) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
          ?.createNotification(
            EmbeddingsBundle.getMessage("ml.embeddings.notification.model.downloading.failed.title"),
            EmbeddingsBundle.getMessage("ml.embeddings.notification.model.downloading.failed.content"),
            NotificationType.WARNING
          )
          ?.notify(null)
      }
      logger.warn("Failed to download embedding models")
    }

    fun getOsId(): String = when {
      SystemInfoRt.isLinux -> "linux"
      SystemInfoRt.isMac -> "macos"
      SystemInfoRt.isWindows -> "windows"
      else -> throw UnsupportedOSException(SystemInfoRt.OS_NAME)
    }

    fun getArchitectureId(): String = when (CpuArch.CURRENT) {
      CpuArch.X86 -> "x86_64"
      CpuArch.ARM64 -> "arm_64"
      else -> throw UnsupportedArchitectureException(System.getProperty("os.arch"))
    }
  }
}

sealed interface DownloadableArtifact {
  val archiveName: String
  val downloadLink: String
  val destination: Path
  // TODO: add signature link

  fun checkPresent(): Boolean
}

sealed class ModelArtifact(
  val name: String,
  private val weightsPath: String,
  private val vocabPath: String,
) : DownloadableArtifact {
  data object SmallModelArtifact : ModelArtifact("small", "dan_100k_optimized.onnx", "bert-base-uncased.txt")

  final override val archiveName = "$name.zip"
  override val downloadLink: String = listOf(CDN_LINK_BASE, MODEL_VERSION, archiveName).joinToString(separator = "/")
  override val destination: Path = LocalArtifactsManager.modelsRoot / name

  override fun checkPresent(): Boolean {
    // TODO: add signature check
    return destination.isDirectory() && listOf(weightsPath, vocabPath).all { destination.resolve(it).exists() }
  }

  fun getVocabPath(): Path = destination / vocabPath
  fun getWeightsPath(): Path = destination / weightsPath

  companion object {
    private const val CDN_LINK_BASE = "https://download.jetbrains.com/resources/ml/full-line/models/embeddings"
  }
}

data object NativeServerArtifact : DownloadableArtifact {
  private const val BINARY_NAME = "embeddings-server"
  private const val CDN_LINK_BASE = "https://download.jetbrains.com/resources/ml/full-line/servers"

  override val archiveName: String = "embeddings-server.zip"
  override val downloadLink: String = listOf(
    CDN_LINK_BASE, SERVER_VERSION, getOsId(), getArchitectureId(), archiveName).joinToString(separator = "/")
  override val destination: Path = LocalArtifactsManager.serverRoot

  fun getBinaryPath(): Path = when {
    SystemInfoRt.isMac -> destination / "$BINARY_NAME.app" / "Contents" / "MacOS" / BINARY_NAME
    SystemInfoRt.isWindows -> destination / "$BINARY_NAME.exe"
    else -> destination / BINARY_NAME
  }

  override fun checkPresent(): Boolean {
    return destination.isDirectory() && getBinaryPath().exists()
  }
}