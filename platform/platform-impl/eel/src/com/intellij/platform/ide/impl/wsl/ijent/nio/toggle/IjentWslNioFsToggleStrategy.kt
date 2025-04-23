// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider
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
import java.util.function.BiConsumer

@ApiStatus.Internal
@VisibleForTesting
class IjentWslNioFsToggleStrategy(
  private val coroutineScope: CoroutineScope,
) {
  internal val enabledInDistros: MutableSet<WSLDistribution> = ContainerUtil.newConcurrentSet()

  private val providersCache = ContainerUtil.createConcurrentWeakMap<String, IjentWslNioFileSystemProvider>()

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      unregisterAll()
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
    recomputeEel(distro) { _, actualFs ->
      actualFs
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

    recomputeEel(distro) { underlyingProvider, _ ->
      val fileSystemProvider = providersCache.computeIfAbsent(distro.id) {
        IjentWslNioFileSystemProvider(
          wslDistribution = distro,
          ijentFsProvider = ijentFsProvider,
          originalFsProvider = TracingFileSystemProvider(underlyingProvider),
        )
      }
      val fileSystem = fileSystemProvider.getFileSystem(distro.getUNCRootPath().toUri())
      LOG.info("Switching $distro to IJent WSL nio.FS: $fileSystem")
      fileSystem
    }
  }

  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    recomputeEel(distro) { underlyingProvider, previousFs ->
      LOG.info("Switching $distro to the original file system but with tracing")

      try {
        previousFs?.close()
      }
      catch (_: UnsupportedOperationException) {
        // It is expected that the default file system always throws that exception on calling close(),
        // but for the sake of following contracts, this method is nonetheless called.
      }
      TracingFileSystemProvider(underlyingProvider).getLocalFileSystem()
    }
  }

  fun unregisterAll() {
    val service = EelNioBridgeService.getInstanceSync()

    val distros = mutableListOf<WSLDistribution>()
    enabledInDistros.removeIf {
      distros += it
      true
    }
    for (distro in distros) {
      service.unregister(WslEelDescriptor(distro))
    }
  }
}

private fun FileSystemProvider.getLocalFileSystem(): FileSystem = getFileSystem(URI.create("file:/"))

private val LOG = logger<IjentWslNioFsToggleStrategy>()

private val WSLDistribution.roots: Set<String>
  get() {
    val localRoots = mutableSetOf(getWindowsPath("/"))
    localRoots.single().let {
      localRoots += it.replace("wsl.localhost", "wsl$")
      localRoots += it.replace("wsl$", "wsl.localhost")
    }
    return localRoots
  }

private fun recomputeEel(
  distro: WSLDistribution,
  action: (underlyingProvider: FileSystemProvider, previousFs: FileSystem?) -> FileSystem?,
) {
  val service = EelNioBridgeService.getInstanceSync()
  val descriptor = WslEelDescriptor(distro)

  distro.roots.forEachGuaranteed { localRoot ->
    service.register(localRoot, descriptor, distro.id, false, false, action)
  }
}