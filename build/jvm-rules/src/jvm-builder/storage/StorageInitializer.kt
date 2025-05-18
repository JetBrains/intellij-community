// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.storage

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.jetbrains.bazel.jvm.mvStore.LongDataType
import org.jetbrains.bazel.jvm.mvStore.VarIntDataType
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.impl.BazelBuildTargetStateManager
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestProperty
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

internal class ToolOrStorageFormatChanged(reason: String) : RuntimeException(reason)

private const val configurationDigestMapName = "configuration-digest"
private val configurationDigestMapBuilder = createConfigurationDigestMapBuilder()
private val configurationDigestSingleMapBuilder = createConfigurationDigestMapBuilder().singleWriter()

private fun createConfigurationDigestMapBuilder(): MVMap.Builder<Int, Long> {
  return MVMap.Builder<Int, Long>().keyType(VarIntDataType).valueType(LongDataType)
}

private const val TOOL_VERSION: Int = 3

internal class StorageInitializer(private val dataDir: Path, private val dbFile: Path) {
  companion object {
    internal fun getTrashDirectory(dataDir: Path): Path = dataDir.resolve("_trash")
  }

  private var wasCleared = false

  suspend fun createBuildDataManager(
    isRebuild: Boolean,
    relativizer: PathRelativizerService,
    buildDataProvider: BazelBuildDataProvider,
    span: Span,
    targetDigests: TargetConfigurationDigestContainer,
  ): BuildDataManager {
    var clearTrash = false
    if (isRebuild) {
      wasCleared = true
      withContext(Dispatchers.IO) {
        deleteOrMoveRecursively(dataDir, getTrashDirectory(dataDir))
      }
    }
    else {
      clearTrash = true
    }

    while (true) {
      try {
        val containerManager = withContext(Dispatchers.IO) {
          Files.createDirectories(dataDir)
          val store = BazelPersistentMapletFactory.openStore(dbFile = dbFile, span = span)
          executeOrCloseStorage(AutoCloseable(store::closeImmediately)) {
            checkVersionAndConfigurationDigest(store = store, isRebuild = isRebuild, targetDigests = targetDigests)
          }

          BazelPersistentMapletFactory.createFactory(store = store, pathRelativizer = relativizer.typeAwareRelativizer!!)
        }

        if (clearTrash) {
          // clear trash only
          withContext(Dispatchers.IO) {
            getTrashDirectory(dataDir).takeIf(Files::exists)?.let { trashDir ->
              for (file in Files.newDirectoryStream(trashDir).use { it.toList() }) {
                Files.deleteIfExists(file)
              }
            }
          }
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
      catch (e: ToolOrStorageFormatChanged) {
        throw e
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

private fun checkVersionAndConfigurationDigest(store: MVStore, isRebuild: Boolean, targetDigests: TargetConfigurationDigestContainer) {
  if (isRebuild) {
    store.storeVersion = TOOL_VERSION
    initMap(store, targetDigests)
    return
  }

  if (store.storeVersion != TOOL_VERSION) {
    throw ToolOrStorageFormatChanged("bazel builder version or storage version")
  }

  val isInitial = !store.hasMap(configurationDigestMapName)
  if (isInitial) {
    initMap(store, targetDigests)
  }
  else {
    val map = store.openMap(configurationDigestMapName, configurationDigestMapBuilder)
    val rebuildRequested = checkConfiguration(metadata = map, targetDigests = targetDigests)
    if (rebuildRequested != null) {
      throw ToolOrStorageFormatChanged(rebuildRequested)
    }
  }
}

private fun initMap(store: MVStore, targetDigests: TargetConfigurationDigestContainer) {
  val map = store.openMap(configurationDigestMapName, configurationDigestSingleMapBuilder)
  for (kind in TargetConfigurationDigestProperty.entries) {
    map.append(kind.ordinal, targetDigests.get(kind))
  }
}

private fun deleteOrMoveRecursively(dataDir: Path, trashDir: Path) {
  if (Files.notExists(dataDir)) {
    return
  }

  Files.walkFileTree(dataDir, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (!Files.deleteIfExists(file) && Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !file.startsWith(trashDir)) {
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

// returns a reason to force rebuild
private fun checkConfiguration(
  metadata: MVMap<Int, Long>,
  targetDigests: TargetConfigurationDigestContainer,
): String? {
  val digestProperties = TargetConfigurationDigestProperty.entries
  if (metadata.size != digestProperties.size) {
    return "configuration digest mismatch: expected metadata size ${digestProperties.size}, got ${metadata.size}"
  }

  val cursor = metadata.cursor(null)
  while (cursor.hasNext()) {
    val key = cursor.next()
    val kind = digestProperties.getOrNull(key)
    val actualHash = kind?.let { targetDigests.get(it) } ?: return "configuration digest mismatch: unknown kind $key"
    val storedHash = cursor.value
    if (actualHash != storedHash) {
      return "configuration digest mismatch (${kind.description}): expected $actualHash, got $storedHash"
    }
  }
  return null
}

private class BazelBuildDataPaths(private val dir: Path) : BuildDataPaths {
  override fun getDataStorageDir() = dir

  override fun getTargetsDataRoot(): Path = dir

  override fun getTargetTypeDataRootDir(targetType: BuildTargetType<*>): Path = dir

  override fun getTargetDataRootDir(target: BuildTarget<*>): Path = dir

  override fun getTargetDataRoot(targetType: BuildTargetType<*>, targetId: String): Path = dir
}