// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.storage

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.impl.BazelBuildTargetStateManager
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.incremental.storage.BuildTargetsState
import java.nio.file.Files
import java.nio.file.Path

internal class StorageInitializer(private val dataDir: Path, private val dbFile: Path) {
  private var wasCleared = false

  suspend fun createBuildDataManager(
    isRebuild: Boolean,
    relativizer: PathRelativizerService,
    buildDataProvider: BazelBuildDataProvider,
    span: Span,
  ): BuildDataManager {
    if (isRebuild) {
      wasCleared = true
      withContext(Dispatchers.IO) {
        FileUtilRt.deleteRecursively(dataDir)
      }
    }

    while (true) {
      try {
        val containerManager = withContext(Dispatchers.IO) {
          Files.createDirectories(dataDir)
          BazelPersistentMapletFactory.open(dbFile = dbFile, pathRelativizer = relativizer.typeAwareRelativizer!!, span = span)
        }

        return executeOrCloseStorage(containerManager) {
          BuildDataManager.open(
            dataPaths = BazelBuildDataPaths(dataDir),
            targetState = BuildTargetsState(BazelBuildTargetStateManager),
            relativizer = relativizer,
            dataManager = buildDataProvider,
            containerFactory = containerManager,
          )
        }
      }
      catch (e: Throwable) {
        if (wasCleared) {
          throw e
        }

        span.recordException(e, Attributes.of(AttributeKey.stringKey("message"), "cannot open cache storage"))
      }

      clearStorage()
    }
  }

  fun clearStorage() {
    wasCleared = true
    // todo rename and store
    FileUtilRt.deleteRecursively(dataDir)
  }
}

private class BazelBuildDataPaths(private val dir: Path) : BuildDataPaths {
  override fun getDataStorageDir() = dir

  override fun getTargetsDataRoot(): Path = dir

  override fun getTargetTypeDataRootDir(targetType: BuildTargetType<*>): Path = dir.resolve(targetType.typeId)

  override fun getTargetDataRootDir(target: BuildTarget<*>): Path = dir.resolve(target.targetType.typeId)

  override fun getTargetDataRoot(targetType: BuildTargetType<*>, targetId: String): Path = dir
}