// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystem
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

internal class IjentWslNioFsToggleStrategy(
  multiRoutingFileSystemProvider: FileSystemProvider,
  private val coroutineScope: CoroutineScope,
) {
  private val ownFileSystems = OwnFileSystems(multiRoutingFileSystemProvider)

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      unregisterAll()
    }
  }

  suspend fun enableForAllWslDistributions() {
    val listener = BiConsumer<Set<WSLDistribution>, Set<WSLDistribution>> { old, new ->
      // TODO The code is race prone. Frequent creations and deletions of WSL containers may break the state.
      for (distro in new - old) {
        coroutineScope.launch {
          handleWslDistributionAddition(distro)
        }
      }
      for (distro in old - new) {
        handleWslDistributionDeletion(distro)
      }
    }

    val wslDistributionManager = WslDistributionManager.getInstance()
    wslDistributionManager.addWslDistributionsChangeListener(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      wslDistributionManager.removeWslDistributionsChangeListener(listener)
    }

    coroutineScope {
      for (distro in wslDistributionManager.installedDistributions) {
        launch {
          handleWslDistributionAddition(distro)
        }
      }
    }
  }

  private suspend fun handleWslDistributionAddition(distro: WSLDistribution) {
    val ijentApi = WslIjentManager.instanceAsync().getIjentApi(distro, null, false)
    switchToIjentFs(distro, ijentApi.id)
  }

  private fun handleWslDistributionDeletion(distro: WSLDistribution) {
    ownFileSystems.compute(distro) { _, ownFs, actualFs ->
      if (ownFs == actualFs) null
      else actualFs
    }
  }

  fun switchToIjentFs(distro: WSLDistribution, ijentId: IjentId) {
    val ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    try {
      ijentFsProvider.newFileSystem(ijentId.uri, null)
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    ownFileSystems.compute(distro) { underlyingProvider, _, actualFs ->
      if (actualFs is IjentWslNioFileSystem) {
        actualFs
      }
      else {
        IjentWslNioFileSystemProvider(
          ijentId = ijentId,
          wslLocalRoot = underlyingProvider.getLocalFileSystem().getPath(distro.getWindowsPath("/")),
          ijentFsProvider = ijentFsProvider,
          originalFsProvider = TracingFileSystemProvider(underlyingProvider),
        ).getFileSystem(ijentId.uri)
      }
    }
  }

  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    ownFileSystems.compute(distro) { underlyingProvider, _, _ ->
      TracingFileSystemProvider(underlyingProvider).getLocalFileSystem()
    }
  }

  fun unregisterAll() {
    ownFileSystems.unregisterAll()
  }
}

private fun FileSystemProvider.getLocalFileSystem(): FileSystem =
  getFileSystem(URI.create("file:/"))

/**
 * This class accesses two synchronization primitives simultaneously.
 * Encapsulation helps to reduce the probability of deadlocks.
 */
private class OwnFileSystems(private val multiRoutingFileSystemProvider: FileSystemProvider) {
  /** The key is a UNC root */
  private val own: MutableMap<String, FileSystem> = ConcurrentHashMap()

  fun compute(
    distro: WSLDistribution,
    compute: (underlyingProvider: FileSystemProvider, ownFs: FileSystem?, actualFs: FileSystem?) -> FileSystem?,
  ) {
    compute(distro.getWindowsPath("/"), compute)
  }

  fun compute(
    root: String,
    compute: (underlyingProvider: FileSystemProvider, ownFs: FileSystem?, actualFs: FileSystem?) -> FileSystem?,
  ) {
    MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, root, false, false) { underlyingProvider, actualFs ->
      own.compute(root) { _, localFs ->
        compute(underlyingProvider, localFs, actualFs)
      }
    }
  }

  fun unregisterAll() {
    own.entries.forEachGuaranteed { (root, ownFs) ->
      compute(root) { _, localFs, actualFs ->
        if (actualFs == localFs) null
        else actualFs
      }
      try {
        ownFs.close()
      }
      catch (_: UnsupportedOperationException) {
        // Do nothing.
      }
    }
  }
}