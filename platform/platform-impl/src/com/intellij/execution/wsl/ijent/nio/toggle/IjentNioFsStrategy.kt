// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystem
import com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.provider.EelNioBridgeService
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
import java.util.function.BiConsumer

@ApiStatus.Internal
@VisibleForTesting
class IjentWslNioFsToggleStrategy(
  private val coroutineScope: CoroutineScope,
) {
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

    recomputeEel(distro) { underlyingProvider, previousFs ->
      if (previousFs is IjentWslNioFileSystem) {
        LOG.debug {
          "Tried to switch $distro to IJent WSL nio.FS, but it had already been so. The old filesystem: $previousFs"
        }
        previousFs
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
    recomputeEel(distro) { underlyingProvider, previousFs ->
      LOG.info("Switching $distro to the original file system but with tracing")

      previousFs?.close()
      TracingFileSystemProvider(underlyingProvider).getLocalFileSystem()
    }
  }

  fun unregisterAll() {
    enabledInDistros.forEachGuaranteed { distro ->
      val descriptor = WslEelDescriptor(distro)
      val service = ApplicationManager.getApplication().service<EelNioBridgeService>()
      service.deregister(descriptor)
    }
  }
}

private fun FileSystemProvider.getLocalFileSystem(): FileSystem =
  getFileSystem(URI.create("file:/"))

private val LOG = logger<IjentWslNioFsToggleStrategy>()

private fun recomputeEel(distro: WSLDistribution, action: (underlyingProvider: FileSystemProvider, previousFs: FileSystem?) -> FileSystem?) {
  val service = ApplicationManager.getApplication().service<EelNioBridgeService>()
  val localRoot = distro.getWindowsPath("/")
  val descriptor = WslEelDescriptor(distro)
  service.register(localRoot, descriptor, false, false, action)
}