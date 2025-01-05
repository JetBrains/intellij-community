// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.util.io.FileUtilRt
import kotlinx.coroutines.ensureActive
import org.h2.mvstore.MVStore
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.loadJpsProject
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.checkConfiguration
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.jps.incremental.storage.StoreLogger
import org.jetbrains.jps.incremental.storage.tryOpenMvStore
import org.jetbrains.jps.model.JpsModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

internal class StorageInitializer(private val dataDir: Path, private val classOutDir: Path) {
  private var storageManager: StorageManager? = null
  private val cacheDbFile = dataDir.resolve("jps-portable-cache.db")

  var isCheckRebuildRequired: Boolean = true
    private set

  private var wasCleared = false

  var isCleanBuild: Boolean = false
    private set

  fun clearAndInit(messageHandler: ConsoleMessageHandler): StorageManager {
    isCheckRebuildRequired = false
    wasCleared = true
    isCleanBuild = true

    clearStorage()

    Files.createDirectories(dataDir)
    val logger = createLogger(messageHandler)
    val store = tryOpenMvStore(file = cacheDbFile, readOnly = false, autoCommitDelay = 0, logger = logger)
    return StorageManager(cacheDbFile, store)
      .also { storageManager = it }
  }

  suspend fun init(messageHandler: ConsoleMessageHandler, targetDigests: TargetConfigurationDigestContainer): StorageManager {
    val logger = createLogger(messageHandler)
    coroutineContext.ensureActive()

    isCleanBuild = Files.notExists(cacheDbFile)

    if (isCleanBuild && Files.isDirectory(dataDir)) {
      messageHandler.info("remove $dataDir and $classOutDir because no cache db file found: $cacheDbFile")
      // if no db file, make sure that data dir is also not reused
      deleteDirs()
    }
    Files.createDirectories(dataDir)

    var store = try {
      tryOpenMvStore(file = cacheDbFile, readOnly = false, autoCommitDelay = 0, logger = logger)
    }
    catch (e: Throwable) {
      messageHandler.info("rebuild due to internal error: ${e.stackTraceToString()}")
      clearStorage()

      return StorageManager(cacheDbFile, createStoreAfterClear(logger))
        .also { storageManager = it }
    }

    val reasonToRebuild = try {
      checkConfiguration(store, targetDigests)
    }
    catch (e: Throwable) {
      store.closeImmediately()
      "Cannot open cache storage: ${e.stackTraceToString()}"
    }

    if (reasonToRebuild != null) {
      store.closeImmediately()

      messageHandler.info("rebuild: $reasonToRebuild")
      clearStorage()
      store = createStoreAfterClear(logger)
    }

    return StorageManager(cacheDbFile, store)
      .also { storageManager = it }
  }

  private suspend fun createStoreAfterClear(logger: StoreLogger): MVStore {
    require(wasCleared)

    coroutineContext.ensureActive()

    Files.createDirectories(dataDir)
    return tryOpenMvStore(file = cacheDbFile, readOnly = false, autoCommitDelay = 0, logger = logger)
  }

  private fun createLogger(messageHandler: ConsoleMessageHandler): StoreLogger {
    return { m: String, e: Throwable, isWarn: Boolean ->
      val message = "$m: ${e.stackTraceToString()}"
      if (isWarn) {
        messageHandler.warn(message)
      }
      else {
        messageHandler.error(message)
      }
    }
  }

  fun createProjectDescriptor(
    messageHandler: ConsoleMessageHandler,
    jpsModel: JpsModel,
    moduleTarget: BazelModuleBuildTarget,
    relativizer: PathRelativizerService,
  ): ProjectDescriptor {
    try {
      return loadJpsProject(
        dataStorageRoot = dataDir,
        // alwaysScanFS doesn't matter, we use our own version of `BuildOperations.ensureFSStateInitialized`,
        // see `JpsProjectBuilder.ensureFsStateInitialized`
        fsState = BuildFSState(/* alwaysScanFS = */ true),
        jpsModel = jpsModel,
        moduleTarget = moduleTarget,
        relativizer = relativizer,
        storageManager = storageManager!!,
      )
    }
    catch (e: Throwable) {
      storageManager!!.forceClose()
      if (wasCleared) {
        throw e
      }

      messageHandler.warn("Cannot open cache storage: ${e.stackTraceToString()}")
    }

    return createProjectDescriptor(
      messageHandler = messageHandler,
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
    )
  }

  private fun clearStorage() {
    isCleanBuild = true
    wasCleared = true
    isCheckRebuildRequired = false
    // todo rename and store
    deleteDirs()
  }

  private fun deleteDirs() {
    FileUtilRt.deleteRecursively(dataDir)
    FileUtilRt.deleteRecursively(classOutDir)
  }
}