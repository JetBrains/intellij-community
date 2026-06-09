// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.nioFs.impl.MultiRoutingFileSystemBackend
import com.intellij.platform.eel.provider.EelAlternativeRootProvider
import com.intellij.platform.eel.provider.EelEnvironmentInitializer
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.ijentFailSafeFileSystemApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems.getDefault
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val useNewFileSystem = System.getProperty("wsl.use.new.filesystem") != null

private val WSLDistribution.roots: Set<String>
  get() {
    val localRoots = mutableSetOf(getWindowsPath("/"))
    localRoots.single().let {
      localRoots += it.replace("wsl.localhost", "wsl$")
      localRoots += it.replace("wsl$", "wsl.localhost")
    }
    return localRoots
  }

@ApiStatus.Internal
@VisibleForTesting
class EelWslMrfsBackend(private val coroutineScope: CoroutineScope) : MultiRoutingFileSystemBackend {
  private val providersCache = ContainerUtil.createConcurrentWeakMap<String, FileSystem>()


  private val reportedNonExistentWslIds = AtomicReference<List<String>>(listOf())

  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    val (wslRoot, distributionId) = WslPathParser.parsePath(sanitizedPath) ?: return null

    try {
      if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
        return null
      }
    }
    catch (err: Exception) {
      if (err is ControlFlowException) {
        return null
      }
      else {
        throw err
      }
    }

    return providersCache.computeIfAbsent(wslRoot) {
      val ijentUri = URI("ijent", "wsl", "/$distributionId", null, null)

      val ijentFsProvider = IjentNioFileSystemProvider.getInstance()

      val descriptor = WslEelDescriptor(WSLDistribution(distributionId), wslRoot)
      try {
        val ijentFs = ijentFailSafeFileSystemApi(coroutineScope, descriptor, checkIsIjentInitialized = {
          WslIjentManager.getInstance().isIjentInitialized(descriptor)
        })
        val fs = ijentFsProvider.newFileSystem(ijentUri, IjentNioFileSystemProvider.newFileSystemMap(ijentFs))

        coroutineScope.coroutineContext.job.invokeOnCompletion {
          fs.close()
        }
      }
      catch (_: FileSystemAlreadyExistsException) {
        // Nothing.
      }

      try {
        val fileSystem = if (useNewFileSystem) {
          IjentEphemeralRootAwareFileSystemProvider(
            root = Path(wslRoot),
            ijentFsProvider = ijentFsProvider,
            originalFsProvider = localFS.provider(),
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
            eelDescriptor = descriptor
          ).getFileSystem(ijentUri)
        }
        else {
          IjentWslNioFileSystemProvider(
            wslId = distributionId,
            ijentFsProvider = ijentFsProvider,
            originalFsProvider = localFS.provider(),
            eelDescriptor = descriptor,
          ).getFileSystem(Path.of(wslRoot).toUri())
        }
        LOG.info("Switching $distributionId to IJent WSL nio.FS: $fileSystem")
        fileSystem
      }
      catch (err: FileSystemNotFoundException) {
        if (
          distributionId !in reportedNonExistentWslIds.getAndUpdate { prev -> if (distributionId in prev) prev else prev + distributionId }
        ) {
          LOG.warn("Attempt to get IJent WSL nio.FS for non-existing WSL distribution $wslRoot", err)
        }
        null
      }
    }
  }

  override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> {
    // TODO Describe why it's fine to return local paths here.
    // TODO Speed up.

    // This code deliberately returns only `\\wsl.localhost\` paths despite the existence of `\\wsl$`.
    // This function is often used in UI like file choosers, and it looks awkward
    // if the same root appears in the file tree twice.
    // The disadvantage is that `Path.of("""\\wsl$\Ubuntu\home").root` is not in `getRootDirectories()`,
    // but the default Windows file system behaves exactly the same:
    // its `getRootDirectories()` never returns WSL roots at all.
    return WslDistributionManager.getInstance().installedDistributionsFuture.getNow(listOf()).map { wsl ->
      wsl.roots.first { root ->
        root.contains("wsl.localhost")
      }
    }
  }

  override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> {
    return WslDistributionManager.getInstance().installedDistributionsFuture.getNow(listOf())
      .flatMap { it.roots }
      .flatMap { compute(localFS, it)!!.fileStores }
  }

  companion object {
    private val LOG = logger<EelWslMrfsBackend>()
  }
}

@ApiStatus.Internal
@VisibleForTesting
class WslEelEnvironmentInitializer : EelEnvironmentInitializer {
  override suspend fun tryInitialize(eelDescriptor: EelDescriptor): EelMachine? {
    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return null
    }

    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return null
    }

    val descriptor = eelDescriptor as? WslEelDescriptor ?: return null

    val project = ProjectManager.getInstance().openProjects.find { project ->
      project.getEelDescriptor() == descriptor
    }

    WslIjentManager.instanceAsync().getIjentApi(descriptor, descriptor.distribution, project, false)

    (getDefault().provider() as MultiRoutingFileSystemProvider).theOnlyFileSystem.getBackend(descriptor.fsRoot)

    return WslEelMachine(descriptor.distribution)
  }
}

@ApiStatus.Internal
class WslEelAlternativeRootProvider : EelAlternativeRootProvider {
  override fun getAlternativeRoots(descriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? =
    (descriptor as? WslEelDescriptor)?.distribution?.roots
}

@ApiStatus.Internal
object WslPathParser {
  // wsl root -> distribution id
  internal fun parsePath(sanitizedPath: String): Pair<String, String>? {
    @MultiRoutingFileSystemPath
    val wslRoot: String
    val distributionId: String

    val serverNameEndIdx = when {
      sanitizedPath.startsWith("//wsl.localhost/", ignoreCase = true) -> 16
      sanitizedPath.startsWith("//wsl$/", ignoreCase = true) -> 7
      else -> return null
    }

    val shareNameEndIdx = sanitizedPath.indexOf('/', startIndex = serverNameEndIdx)

    if (shareNameEndIdx == -1) {
      wslRoot = sanitizedPath + "\\"
      distributionId = sanitizedPath.substring(serverNameEndIdx)
    }
    else {
      wslRoot = sanitizedPath.take(shareNameEndIdx + 1)
      distributionId = sanitizedPath.substring(serverNameEndIdx, shareNameEndIdx)
    }

    return wslRoot to distributionId
  }
}

class WslEelDescriptor internal constructor(val distribution: WSLDistribution, fsRoot: String) : EelPathBoundDescriptor {
  internal val fsRoot = fsRoot.replace('/', '\\')

  constructor(distribution: WSLDistribution) : this(distribution, distribution.getUNCRootPath().pathString)

  override val rootPath: Path get() = fsRoot.let(::Path)
  override val name: @NonNls String = "WSL: ${distribution.presentableName}"

  override val osFamily: EelOsFamily = EelOsFamily.Posix

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WslEelDescriptor

    if (distribution != other.distribution) return false
    if (fsRoot != other.fsRoot) return false

    return true
  }

  override fun hashCode(): Int {
    var result = distribution.hashCode()
    result = 31 * result + fsRoot.hashCode()
    return result
  }

  override fun toString(): String = "WslEelDescriptor(distribution=$distribution, fsRoot='$fsRoot')"
}
