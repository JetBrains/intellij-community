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
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

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
        deleteOrMoveRecursively(dataDir, getTrashDirectory(dataDir))
      }
    }
    else {  // clear trash only
      withContext(Dispatchers.IO) {
        getTrashDirectory(dataDir).takeIf(Files::exists)?.let { trashDir ->
          Files.newDirectoryStream(trashDir).use {
            it.forEach(::tryDeleteFile)
          }
        }
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

  companion object {
    fun getTrashDirectory(dataDir: Path): Path = dataDir.resolve("_trash")

    private fun deleteOrMoveRecursively(dataDir: Path, trashDir: Path) {
      if (!Files.exists(dataDir)) {
        return
      }

      Files.walkFileTree(dataDir, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (!tryDeleteFile(file) && !file.startsWith(trashDir)) {
            Files.createDirectories(trashDir)
            val tempFile = Files.createTempFile(trashDir, null, null)
            Files.move(file, tempFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
          }
          return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
          if (exc != null) {
            throw exc
          }

          try {
            Files.deleteIfExists(dir)
          }
          catch (e: DirectoryNotEmptyException) {
            if (dir == trashDir || Files.exists(trashDir) && dir == dataDir) {
              // ignore
            }
            else {
              throw e
            }
          }
          return FileVisitResult.CONTINUE
        }
      })
    }
  }
}

private fun tryDeleteFile(file: Path): Boolean {
  return file.toFile().delete() || !Files.exists(file, LinkOption.NOFOLLOW_LINKS)
}

private class BazelBuildDataPaths(private val dir: Path) : BuildDataPaths {
  override fun getDataStorageDir() = dir

  override fun getTargetsDataRoot(): Path = dir

  override fun getTargetTypeDataRootDir(targetType: BuildTargetType<*>): Path = dir

  override fun getTargetDataRootDir(target: BuildTarget<*>): Path = dir

  override fun getTargetDataRoot(targetType: BuildTargetType<*>, targetId: String): Path = dir
}