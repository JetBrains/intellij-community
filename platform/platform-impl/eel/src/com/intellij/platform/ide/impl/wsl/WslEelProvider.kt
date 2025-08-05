// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.execution.wsl.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.*
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.impl.fs.EelEarlyAccessChecker
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.*
import java.nio.file.FileSystems.getDefault
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val WSLDistribution.roots: Set<String>
  get() {
    val localRoots = mutableSetOf(getWindowsPath("/"))
    localRoots.single().let {
      localRoots += it.replace("wsl.localhost", "wsl$")
      localRoots += it.replace("wsl$", "wsl.localhost")
    }
    return localRoots
  }

private suspend fun WSLDistribution.getIjent(descriptor: EelDescriptor): IjentPosixApi {
  return WslIjentManager.instanceAsync().getIjentApi(descriptor, this, null, false)
}

@ApiStatus.Internal
@VisibleForTesting
class EelWslMrfsBackend(private val coroutineScope: CoroutineScope) : MultiRoutingFileSystemBackend {
  private val providersCache = ContainerUtil.createConcurrentWeakMap<String, FileSystem>()

  private val useNewFileSystem by lazy {
    Registry.`is`("wsl.use.new.filesystem")
  }

  private val reportedNonExistentWslIds = AtomicReference<List<String>>(listOf())

  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    val (wslRoot, distributionId) = WslEelProvider.parsePath(sanitizedPath) ?: return null

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

    val key = if (useNewFileSystem) wslRoot else distributionId
    return providersCache.computeIfAbsent(key) {
      service<EelEarlyAccessChecker>().check(sanitizedPath)

      val ijentUri = URI("ijent", "wsl", "/$distributionId", null, null)

      val ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())

      try {
        val ijentFs = IjentFailSafeFileSystemPosixApi(coroutineScope, WslEelDescriptor(WSLDistribution(distributionId), wslRoot))
        val fs = ijentFsProvider.newFileSystem(ijentUri, IjentNioFileSystemProvider.newFileSystemMap(ijentFs))

        coroutineScope.coroutineContext.job.invokeOnCompletion {
          fs?.close()
        }
      }
      catch (_: FileSystemAlreadyExistsException) {
        // Nothing.
      }

      try {
        val fileSystem = if (Registry.`is`("wsl.use.new.filesystem")) {
          IjentEphemeralRootAwareFileSystemProvider(
            root = Path(wslRoot),
            ijentFsProvider = ijentFsProvider,
            originalFsProvider = TracingFileSystemProvider(localFS.provider()),
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
          ).getFileSystem(ijentUri)
        }
        else {
          IjentWslNioFileSystemProvider(
            wslId = distributionId,
            ijentFsProvider = ijentFsProvider,
            originalFsProvider = TracingFileSystemProvider(localFS.provider()),
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
class WslEelProvider : EelProvider {
  override fun getEelDescriptor(path: Path): WslEelDescriptor? {
    val root = path.root ?: return null

    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return null
    }

    val wslPath = WslPath.parseWindowsUncPath(root.toString()) ?: return null

    return WslEelDescriptor(
      WSLDistribution(wslPath.distributionId),
      root.toString()
    )
  }

  override fun getInternalName(eelMachine: EelMachine): String? =
    if (eelMachine is WslEelMachine)
      "WSL-" + eelMachine.distribution.id
    else
      null

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? =
    (eelDescriptor as? WslEelDescriptor)?.distribution?.roots

  override fun getEelMachineByInternalName(internalName: String): EelMachine? =
    if (internalName.startsWith("WSL-"))
      WslEelMachine(WSLDistribution(internalName.substring(4)))
    else
      null

  override suspend fun tryInitialize(@MultiRoutingFileSystemPath path: String) {
    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return
    }

    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return
    }

    val nioPath =
      try {
        Path.of(path)
      }
      catch (_: IllegalArgumentException) {  // TODO What throws it?
        return
      }

    val wslPath = WslPath.parseWindowsUncPath(path) ?: return

    val project = ProjectManager.getInstance().openProjects.find { project ->
      try {
        val basePath = project.basePath?.let(Path::of)
        basePath != null && nioPath.startsWith(basePath)
      }
      catch (_: InvalidPathException) {
        false
      }
    }

    val descriptor = WslEelDescriptor(wslPath.distribution, wslPath.wslRoot)

    WslIjentManager.instanceAsync().getIjentApi(descriptor, wslPath.distribution, project, false)

    (getDefault().provider() as MultiRoutingFileSystemProvider).theOnlyFileSystem.getBackend(wslPath.wslRoot + "\\")
  }

  companion object {
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
}

class WslEelMachine(val distribution: WSLDistribution) : EelMachine {
  override val osFamily: EelOsFamily = EelOsFamily.Posix
  override val name: @NonNls String = "WSL: ${distribution.presentableName}"

  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    check(descriptor is WslEelDescriptor && descriptor.machine == this) {
      "Wrong descriptor: $descriptor for machine: $this"
    }

    return distribution.getIjent(descriptor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WslEelMachine

    if (distribution != other.distribution) return false
    if (osFamily != other.osFamily) return false

    return true
  }

  override fun hashCode(): Int {
    var result = distribution.hashCode()
    result = 31 * result + osFamily.hashCode()
    return result
  }
}

class WslEelDescriptor(val distribution: WSLDistribution, internal val fsRoot: String) : EelPathBoundDescriptor {
  constructor(distribution: WSLDistribution): this(distribution, distribution.getUNCRootPath().pathString)

  override val rootPath: Path get() = fsRoot.let(::Path)
  override val machine: EelMachine = WslEelMachine(distribution)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WslEelDescriptor

    if (fsRoot != other.fsRoot) return false
    if (machine != other.machine) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fsRoot.hashCode()
    result = 31 * result + machine.hashCode()
    return result
  }
}
