// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystem
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

@ApiStatus.Internal
@VisibleForTesting
// TODO: should be merged with IjentNioFsRegistry
class IjentWslNioFsToggleStrategy(
  multiRoutingFileSystemProvider: FileSystemProvider,
  private val coroutineScope: CoroutineScope,
) {
  private val ownFileSystems = OwnFileSystems(multiRoutingFileSystemProvider)
  internal val enabledInDistros: MutableSet<WSLDistribution> = ContainerUtil.newConcurrentSet()

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      unregisterAll()
      enabledInDistros.clear()
    }
  }

  fun enableForAllWslDistributions() {
    val listener = BiConsumer<Set<WSLDistribution>, Set<WSLDistribution>> { old, new ->
      // TODO The code is race prone. Frequent creations and deletions of WSL containers may break the state.
      for (distro in new - old) {
        handleWslDistributionAddition(distro)
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

    for (distro in wslDistributionManager.installedDistributions) {
      handleWslDistributionAddition(distro)
    }
  }

  private fun handleWslDistributionAddition(distro: WSLDistribution) {
    enabledInDistros += distro
    switchToIjentFs(distro)
  }

  private fun handleWslDistributionDeletion(distro: WSLDistribution) {
    enabledInDistros -= distro
    ownFileSystems.compute(distro) { _, ownFs, actualFs ->
      if (ownFs == actualFs) {
        LOG.info("Unregistering a custom filesystem $actualFs from a removed WSL distribution $distro")
        null
      }
      else actualFs
    }
  }

  fun switchToIjentFs(distro: WSLDistribution) {
    val ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    try {
      val ijentFs = IjentFailSafeFileSystemPosixApi(coroutineScope) {
        WslIjentManager.instanceAsync().getIjentApi(distro, null, false)
      }
      ijentFsProvider.newFileSystem(
        URI("ijent", "wsl", "/${distro.id}", null, null),
        IjentNioFileSystemProvider.newFileSystemMap(ijentFs),
      )
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    ownFileSystems.compute(distro) { underlyingProvider, _, actualFs ->
      if (actualFs is IjentWslNioFileSystem) {
        LOG.debug {
          "Tried to switch $distro to IJent WSL nio.FS, but it had already been so. The old filesystem: $actualFs"
        }
        actualFs
      }
      else {
        val fileSystem = IjentWslNioFileSystemProvider(
          wslDistribution = distro,
          ijentFsProvider = ijentFsProvider,
          originalFsProvider = TracingFileSystemProvider(underlyingProvider),
        ).getFileSystem(distro.getUNCRootPath().toUri())
        LOG.info("Switching $distro to IJent WSL nio.FS: $fileSystem")
        fileSystem
      }
    }
  }

  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    ownFileSystems.compute(distro) { underlyingProvider, ownFs, actualFs ->
      LOG.info("Switching $distro to the original file system but with tracing")

      actualFs?.close()
      ownFs?.close()

      TracingFileSystemProvider(underlyingProvider).getLocalFileSystem()
    }
  }

  fun unregisterAll() {
    ownFileSystems.unregisterAll()
  }
}

private fun FileSystemProvider.getLocalFileSystem(): FileSystem =
  getFileSystem(URI.create("file:/"))

private val LOG = logger<IjentWslNioFsToggleStrategy>()

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