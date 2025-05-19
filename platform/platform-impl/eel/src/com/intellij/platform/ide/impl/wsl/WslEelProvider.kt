// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.execution.wsl.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.Path

private val WSLDistribution.roots: Set<String>
  get() {
    val localRoots = mutableSetOf(getWindowsPath("/"))
    localRoots.single().let {
      localRoots += it.replace("wsl.localhost", "wsl$")
      localRoots += it.replace("wsl$", "wsl.localhost")
    }
    return localRoots
  }

private suspend fun WSLDistribution.getIjent(): IjentPosixApi {
  return WslIjentManager.instanceAsync().getIjentApi(this, null, false)
}

@ApiStatus.Internal
class WslEelProvider(private val coroutineScope: CoroutineScope) : EelProvider {
  private val providersCache = ContainerUtil.createConcurrentWeakMap<String, FileSystemProvider>()

  companion object {
    private val LOG = logger<WslEelProvider>()
  }

  override suspend fun tryInitialize(path: String) {
    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return
    }

    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return
    }

    if (!WslPath.isWslUncPath(path)) {
      return
    }

    val allWslDistributions = serviceAsync<WslDistributionManager>().installedDistributions

    val path = Path.of(path)
    val service = EelNioBridgeService.getInstanceSync()
    val descriptor = service.tryGetEelDescriptor(path)

    if (descriptor != null && descriptor !== LocalEelDescriptor) {
      check(descriptor is WslEelDescriptor)
      return
    }

    for (distro in allWslDistributions) {
      val matches =
        try {
          distro.getWslPath(path) != null
        }
        catch (_: IllegalArgumentException) {
          false
        }
      if (matches) {
        service.registerNioWslFs(distro)
      }
    }
  }

  private suspend fun EelNioBridgeService.registerNioWslFs(distro: WSLDistribution) {
    val descriptor = distro.getIjent().descriptor as WslEelDescriptor
    val ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    val ijentUri = URI("ijent", "wsl", "/${distro.id}", null, null)

    try {
      val ijentFs = IjentFailSafeFileSystemPosixApi(coroutineScope) { distro.getIjent() }
      val fs = ijentFsProvider.newFileSystem(ijentUri, IjentNioFileSystemProvider.newFileSystemMap(ijentFs))

      coroutineScope.coroutineContext.job.invokeOnCompletion {
        fs?.close()
      }
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    descriptor.distribution.roots.forEachGuaranteed { localRoot ->
      register(localRoot, descriptor, descriptor.distribution.id, false, false) { underlyingProvider, _ ->
        val fileSystemProvider = providersCache.computeIfAbsent(distro.id) {
          if (Registry.`is`("wsl.use.new.filesystem")) {
            IjentEphemeralRootAwareFileSystemProvider(
              root = Path(localRoot),
              ijentFsProvider = ijentFsProvider,
              originalFsProvider = TracingFileSystemProvider(underlyingProvider),
              // FIXME: is this behavior really correct?
              //
              // It is known that `originalFs.rootDirectories` always returns all WSL drives.
              // Also, it is known that `ijentFs.rootDirectories` returns a single WSL drive,
              // which is already mentioned in `originalFs.rootDirectories`.
              //
              // `ijentFs` is usually represented by `IjentFailSafeFileSystemPosixApi`,
              // which launches IJent and the corresponding WSL containers lazily.
              //
              // This function avoids fetching root directories directly from IJent.
              // This way, various UI file trees don't start all WSL containers during loading the file system root.
              useRootDirectoriesFromOriginalFs = true,
            )
          }
          else {
            IjentWslNioFileSystemProvider(
              wslDistribution = distro,
              ijentFsProvider = ijentFsProvider,
              originalFsProvider = TracingFileSystemProvider(underlyingProvider),
            )
          }
        }
        val fileSystem = if (fileSystemProvider is IjentEphemeralRootAwareFileSystemProvider) {
          fileSystemProvider.getFileSystem(ijentUri)
        }
        else {
          fileSystemProvider.getFileSystem(distro.getUNCRootPath().toUri())
        }
        LOG.info("Switching $distro to IJent WSL nio.FS: $fileSystem")
        fileSystem
      }
    }
  }
}

data class WslEelDescriptor(val distribution: WSLDistribution, override val platform: EelPlatform) : EelDescriptor {

  override suspend fun upgrade(): EelApi {
    return distribution.getIjent()
  }

  override fun equals(other: Any?): Boolean {
    return other is WslEelDescriptor && other.distribution.id == distribution.id
  }

  override fun hashCode(): Int {
    return distribution.id.hashCode()
  }
}
