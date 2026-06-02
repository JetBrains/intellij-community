// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fileChooser

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.universal.FileWatcherAdapter
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import com.intellij.platform.eel.fs.EelFileSystemApi.WatchedPath
import com.intellij.platform.eel.fs.UnwatchOptionsBuilder
import com.intellij.platform.eel.fs.WatchOptionsBuilder
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class EelFileWatcherAdapter : FileWatcherAdapter {
  private val watchedPaths: MutableSet<Path> = ConcurrentHashMap.newKeySet()

  override suspend fun subscribe(path: Path): Flow<FileChangeType>? {
    if (!Files.isDirectory(path) || path.parent == null|| watchedPaths.contains(path)) return null
    return flow {
      try {
        val descriptor = path.getEelDescriptor()
        val eelApi = descriptor.toEelApi()
        val eelPath = path.asEelPath(descriptor)
        val changesFlow = eelApi.fs.watchChanges()
        eelApi.fs.addWatchRoots(
          WatchOptionsBuilder()
            .changeTypes(setOf(FileChangeType.CREATED, FileChangeType.DELETED, FileChangeType.CHANGED))
            .paths(setOf(WatchedPath.from(eelPath)))
            .build()
        )
        watchedPaths.add(path)
        changesFlow.collect { pathChange ->
          emit(pathChange.type)
        }
      }
      catch (e: UnsupportedOperationException) {
        LOG.debug("File watching not supported for $path", e)
      }
      catch (e: Exception) {
        LOG.debug("Error subscribing to changes for $path", e)
      }
    }
  }

  override suspend fun unsubscribe(path: Path) {
    if (!watchedPaths.remove(path)) return
    unwatch(path)
  }

  private suspend fun unwatch(path: Path) {
    try {
      val descriptor = path.getEelDescriptor()
      val eelApi = descriptor.toEelApi()
      val eelPath = path.asEelPath(descriptor)
      eelApi.fs.unwatch(UnwatchOptionsBuilder(eelPath).build())
    }
    catch (e: Exception) {
      LOG.debug("Error unwatching $path", e)
    }
  }

  override suspend fun stop() {
    val paths = watchedPaths.toList()
    watchedPaths.clear()
    if (paths.isNotEmpty()) {
      for (path in paths) {
        unwatch(path)
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(EelFileWatcherAdapter::class.java)
  }
}