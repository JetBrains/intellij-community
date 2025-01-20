// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.ensureActive
import org.h2.mvstore.MVStore
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.impl.loadJpsProject
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

  fun clearAndInit(span: Span): StorageManager {
    isCheckRebuildRequired = false
    wasCleared = true
    isCleanBuild = true

    clearStorage()

    Files.createDirectories(dataDir)
    val logger = createLogger(span)
    val store = tryOpenMvStore(file = cacheDbFile, readOnly = false, autoCommitDelay = 0, logger = logger)
    return StorageManager(cacheDbFile, store)
      .also { storageManager = it }
  }

  suspend fun init(span: Span): StorageManager {
    val logger = createLogger(span)
    coroutineContext.ensureActive()

    isCleanBuild = Files.notExists(cacheDbFile)

    if (isCleanBuild && Files.isDirectory(dataDir)) {
      span.addEvent("remove $dataDir and $classOutDir because no cache db file found: $cacheDbFile")
      // if no db file, make sure that data dir is also not reused
      deleteDirs()
    }
    Files.createDirectories(dataDir)

    val store = try {
      tryOpenMvStore(file = cacheDbFile, readOnly = false, autoCommitDelay = 0, logger = logger)
    }
    catch (e: Throwable) {
      span.recordException(e, Attributes.of(AttributeKey.stringKey("message"), "rebuild due to internal error"))
      clearStorage()

      return StorageManager(cacheDbFile, createStoreAfterClear(logger))
        .also { storageManager = it }
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

  private fun createLogger(span: Span): StoreLogger {
    return { m: String, e: Throwable, isWarn: Boolean ->
      span.recordException(e, Attributes.of(AttributeKey.stringKey("message"), m))
      if (!isWarn) {
        span.setStatus(StatusCode.ERROR)
      }
    }
  }

  fun createProjectDescriptor(
    messageHandler: RequestLog,
    jpsModel: JpsModel,
    moduleTarget: BazelModuleBuildTarget,
    relativizer: PathRelativizerService,
    buildDataProvider: BazelBuildDataProvider,
    span: Span,
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
        buildDataProvider = buildDataProvider,
      )
    }
    catch (e: Throwable) {
      storageManager!!.forceClose()
      if (wasCleared) {
        throw e
      }

      span.recordException(e, Attributes.of(
        AttributeKey.stringKey("message"), "cannot open cache storage",
      ))
    }

    return createProjectDescriptor(
      messageHandler = messageHandler,
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = buildDataProvider,
      span = span,
    )
  }

  fun clearStorage() {
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