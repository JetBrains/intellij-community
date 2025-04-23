// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

private suspend fun WSLDistribution.getIjent(): IjentPosixApi {
  return WslIjentManager.instanceAsync().getIjentApi(this, null, false)
}

@ApiStatus.Internal
@VisibleForTesting
class IjentWslNioFsToggleStrategy(
  private val coroutineScope: CoroutineScope,
) {
  internal val enabledInDistros: MutableMap<WSLDistribution, WslEelDescriptor> = ConcurrentHashMap()

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
    switchToIjentFs(distro)
  }

  private fun handleWslDistributionDeletion(distro: WSLDistribution) {
    val descriptor = enabledInDistros.remove(distro)

    if (descriptor != null) {
      recomputeEel(descriptor) { _, actualFs ->
        actualFs
      }
    }
  }

  fun switchToIjentFs(distro: WSLDistribution) {
    val ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    val descriptor = runBlockingMaybeCancellable { distro.getIjent() }.descriptor as WslEelDescriptor

    enabledInDistros[distro] = descriptor

    try {
      val ijentFs = IjentFailSafeFileSystemPosixApi(coroutineScope) { distro.getIjent() }
      ijentFsProvider.newFileSystem(
        URI("ijent", "wsl", "/${distro.id}", null, null),
        IjentNioFileSystemProvider.newFileSystemMap(ijentFs),
      )
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    recomputeEel(descriptor) { underlyingProvider, _ ->
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

  fun switchToTracingWsl9pFs(descriptor: WslEelDescriptor) {
    recomputeEel(descriptor) { underlyingProvider, previousFs ->
      LOG.info("Switching $descriptor to the original file system but with tracing")

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

    enabledInDistros.entries.forEachGuaranteed { (_, descriptor) ->
      service.unregister(descriptor)
    }

    enabledInDistros.clear()
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
  descriptor: WslEelDescriptor,
  action: (underlyingProvider: FileSystemProvider, previousFs: FileSystem?) -> FileSystem?,
) {
  val service = EelNioBridgeService.getInstanceSync()

  descriptor.distribution.roots.forEachGuaranteed { localRoot ->
    service.register(localRoot, descriptor, descriptor.distribution.id, false, false, action)
  }
}