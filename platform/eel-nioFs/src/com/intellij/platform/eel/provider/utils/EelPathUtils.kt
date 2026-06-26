// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.fs.EelWindowsFileInfo
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelPathTransfer.FileTransferAttributesStrategy
import com.intellij.platform.eel.provider.utils.EelPathTransfer.IncrementalWalkingTransferAbsoluteSymlinkHandler
import com.intellij.platform.eel.provider.utils.EelPathTransfer.incrementalWalkingTransfer
import com.intellij.platform.eel.provider.utils.EelPathUtils.UnixFilePermissionBranch.GROUP
import com.intellij.platform.eel.provider.utils.EelPathUtils.UnixFilePermissionBranch.OTHER
import com.intellij.platform.eel.provider.utils.EelPathUtils.UnixFilePermissionBranch.OWNER
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.AccessMode
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@ApiStatus.Internal
object EelPathUtils {
  /**
   * **Obsolete – avoid it in new code.**
   *
   * Exposes an implementation detail (whether a given *[Path]* is stored on a _local_ or _remote_ Eel),
   * encouraging code that behaves differently for the two cases and therefore becomes **machine-dependent**.
   * New APIs are designed so that callers do **not** have to know where a file physically resides.
   *
   * @return `true` if *path* is located in the local Eel.
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun isPathLocal(path: Path): Boolean {
    return path.getEelDescriptor() == LocalEelDescriptor
  }

  fun expandUserHome(eelDescriptor: EelDescriptor, path: String): Path {
    val userHome = runBlocking { eelDescriptor.toEelApi() }.userInfo.home
    val path = runCatching { Path(path).asEelPath().toString() }.getOrNull() ?: path // try to normalize path

    return eelDescriptor.getPath(if (path == "~") {
      userHome.toString()
    } else if (path.startsWith("~/") || path.startsWith("~\\")) {
      userHome.toString() + path.substring(1)
    } else {
      path
    }).asNioPath()
  }

  private suspend fun createTemporaryDirectoryImpl(
    eelApi: EelApi,
    prefix: String = "",
    suffix: String = "",
    deleteOnExit: Boolean = false,
  ): Path {
    val file = eelApi.fs.createTemporaryDirectory()
      .prefix(prefix)
      .suffix(suffix)
      .deleteOnExit(deleteOnExit)
      .getOrThrowFileSystemException()
    return file.asNioPath()
  }

  @JvmStatic
  fun createTemporaryDirectory(eelApi: EelApi, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    return runBlocking { createTemporaryDirectoryImpl(eelApi, prefix, suffix, deleteOnExit) }
  }

  @JvmStatic
  fun getNioPath(path: String, descriptor: EelDescriptor): Path {
    return EelPath.parse(path, descriptor).asNioPath()
  }

  @JvmStatic
  fun renderAsEelPath(path: Path): String {
    val eelPath = path.asEelPath()
    if (eelPath.descriptor == LocalEelDescriptor) {
      return path.toString()
    }
    return eelPath.toString()
  }

  /** If [path] is `\\wsl.localhost\Ubuntu\mnt\c\Program Files`, then actual path is `C:\Program Files` */
  @JvmStatic
  fun getActualPath(path: Path): Path = path.run {
    if (
      isAbsolute &&
      nameCount >= 2 &&
      getName(0).toString() == "mnt" &&
      getName(1).toString().run { length == 1 && first().isLetter() }
    )
      asSequence()
        .drop(2)
        .map(Path::toString)
        .fold(fileSystem.getPath("${getName(1).toString().uppercase()}:\\"), Path::resolve)
    else
      this
  }

  /**
   * ```kotlin
   * getUriLocalToEel(Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user\\dir")).toString() = "file:/home/user/dir"
   * getUriLocalToEel(Path.of("C:\\User\\dir\\")).toString() = "file:/C:/user/dir"
   * ```
   */
  @JvmStatic
  fun getUriLocalToEel(path: Path): URI {
    val eelPath = path.asEelPath()
    if (eelPath.descriptor == LocalEelDescriptor) {
      // there is not mapping by Eel, hence the path may be considered local
      return path.toUri()
    }
    val root = eelPath.root.toString().replace('\\', '/')
    // see sun.nio.fs.WindowsUriSupport#toUri(java.lang.String, boolean, boolean)
    val trailing = if (eelPath.descriptor.osFamily.isWindows) "/" else ""
    return URI("file", null, trailing + root + eelPath.parts.joinToString("/"), null, null)
  }

  /**
   * Represents a target location for transferring content.
   *
   * Implementations:
   * - [TransferTarget.Explicit]: A target defined by an explicit [Path].
   * - [TransferTarget.Temporary]: A target that is generated as a temporary location based on an [EelDescriptor].
   */
  sealed interface TransferTarget {
    /**
     * Represents an explicit target for content transfer.
     *
     * @property path The explicit [Path] where the content should be transferred.
     */
    data class Explicit(val path: Path) : TransferTarget

    /**
     * Represents a temporary target for content transfer.
     *
     * The target is determined based on the provided [EelDescriptor]. A temporary location will be created
     * in the remote environment using the descriptor information.
     *
     * @property descriptor The [EelDescriptor] to be used for creating a temporary target.
     */
    data class Temporary(val descriptor: EelDescriptor) : TransferTarget
  }

  /**
   * @deprecated Use [transferLocalContentToRemote] instead.
   */
  @ApiStatus.Obsolete
  @JvmStatic
  @JvmOverloads
  fun transferContentsIfNonLocal(
    eel: EelApi,
    source: Path,
    sink: Path? = null,
    fileAttributesStrategy: FileTransferAttributesStrategy = FileTransferAttributesStrategy.Copy,
  ): Path {
    return transferLocalContentToRemote(source, if (sink != null) TransferTarget.Explicit(sink) else TransferTarget.Temporary(eel.descriptor), fileAttributesStrategy)
  }

  /**
   * Synchronizes (transfers) local content from [source] to a remote environment.
   *
   * If the source is already non-local, no transfer is performed and the original [source] is returned.
   * For a local source, content is transferred based on the [target]:
   * - If [target] is [TransferTarget.Temporary], a temporary location is created in the remote environment.
   * - If [target] is [TransferTarget.Explicit], content is transferred to the explicit target [Path].
   *
   * The file attributes are managed according to [fileAttributesStrategy].
   *
   * @param source the local source [Path] whose content is to be synchronized.
   * @param target the transfer target, which can be explicit ([TransferTarget.Explicit]) or temporary ([TransferTarget.Temporary]).
   * @param fileAttributesStrategy strategy for handling file attributes during transfer (default is [FileTransferAttributesStrategy.Copy]).
   * @return a [Path] pointing to the location of the synchronized content in the remote environment.
   */
  @JvmStatic
  @JvmOverloads
  fun transferLocalContentToRemote(
    source: Path,
    target: TransferTarget,
    fileAttributesStrategy: FileTransferAttributesStrategy = FileTransferAttributesStrategy.Copy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler? = null,
  ): Path {
    if (source.getEelDescriptor() !== LocalEelDescriptor) {
      return source
    }

    return when (target) {
      is TransferTarget.Temporary -> {
        if (target.descriptor === LocalEelDescriptor) {
          return source
        }

        runBlocking {
          TransferredContentHolder.transferIfNeeded(target.descriptor.toEelApi(), source, fileAttributesStrategy, absoluteSymlinkHandler)
        }
      }
      is TransferTarget.Explicit -> {
        val sink = target.path
        runBlocking {
          incrementalWalkingTransfer(source, sink, fileAttributesStrategy, absoluteSymlinkHandler, null)
        }
        return sink
      }
    }
  }

  /**
   * Temporary solution: caches are scoped per EelApi instance using WeakIdentityMap.
   *
   * TODO: Ideally, TransferredContentHolder should be bound to the IJent instance (or its CoroutineScope)
   * instead of being an application-level service. This would provide cleaner lifecycle management
   * and explicit cache invalidation on IJent restart.
   */
  private object TransferredContentHolder {

    data class CacheKey(
      val sourcePathString: String,
      val fileAttributesStrategy: FileTransferAttributesStrategy,
      val absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    )

    data class CacheValue(
      val transferredFilePath: Path,
    )

    private class Cache : ConcurrentHashMap<CacheKey, Deferred<CacheValue>>()

    // eel api instance -> (source path string -> transferred file)
    private val caches = CollectionFactory.createConcurrentWeakIdentityMap<EelApi, Cache>()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun transferIfNeeded(
      eel: EelApi,
      source: Path,
      fileAttributesStrategy: FileTransferAttributesStrategy,
      absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    ): Path {
      val cache = caches.computeIfAbsent(eel) { Cache() }

      return cache.compute(CacheKey(source.toString(), fileAttributesStrategy, absoluteSymlinkHandler)) { _, deferred ->
        var targetPath: Path? = null
        if (deferred != null) {
          if (deferred.isCompleted) {
            targetPath = deferred.getCompleted().transferredFilePath
          }
          else {
            return@compute deferred
          }
        }

        GlobalScope.async {
          val targetPath = targetPath ?: eel.createTempFor(source, true)
          incrementalWalkingTransfer(source, targetPath, fileAttributesStrategy, absoluteSymlinkHandler, null)
          CacheValue(targetPath)
        }
      }!!.await().transferredFilePath
    }
  }

  private suspend fun EelApi.createTempFor(source: Path, deleteOnExit: Boolean): Path {
    return if (source.isDirectory()) {
      createTemporaryDirectoryImpl(eelApi = this, deleteOnExit = deleteOnExit)
    }
    else {
      fs.createTemporaryFile().suffix(source.name).deleteOnExit(deleteOnExit).getOrThrowFileSystemException().asNioPath()
    }
  }

  @JvmStatic
  fun getHomePath(descriptor: EelDescriptor): Path {
    // usually eel is already initialized to this moment
    @Suppress("RAW_RUN_BLOCKING")
    val api = runBlocking {
      descriptor.toEelApi()
    }
    val someEelPath = api.fs.user.home
    return someEelPath.asNioPath()
  }

  private enum class UnixFilePermissionBranch { OWNER, GROUP, OTHER }

  /**
   * returns one of [modes] which is not satisfied, or `null` if all modes are satisfied
   */
  @ApiStatus.Internal
  fun checkAccess(userInfo: EelUserPosixInfo, fileInfo: EelPosixFileInfo, vararg modes: AccessMode): AccessMode? {
    if (userInfo.uid == 0) {
      if (AccessMode.EXECUTE in modes) {
        val executable = fileInfo.permissions.run {
          ownerCanExecute || groupCanExecute || otherCanExecute
        }
        if (!executable) {
          return AccessMode.EXECUTE
        }
      }
      return null
    }

    // Inspired by sun.nio.fs.UnixFileSystemProvider#checkAccess
    val filePermissionBranch = when {
      userInfo.uid == fileInfo.permissions.owner -> OWNER
      userInfo.gid == fileInfo.permissions.group -> GROUP
      else -> OTHER
    }

    if (AccessMode.READ in modes) {
      val canRead = when (filePermissionBranch) {
        OWNER -> fileInfo.permissions.ownerCanRead
        GROUP -> fileInfo.permissions.groupCanRead
        OTHER -> fileInfo.permissions.otherCanRead
      }
      if (!canRead) {
        return AccessMode.READ
      }
    }
    if (AccessMode.WRITE in modes) {
      val canWrite = when (filePermissionBranch) {
        OWNER -> fileInfo.permissions.ownerCanWrite
        GROUP -> fileInfo.permissions.groupCanWrite
        OTHER -> fileInfo.permissions.otherCanWrite
      }
      if (!canWrite) {
        return AccessMode.WRITE
      }
    }
    if (AccessMode.EXECUTE in modes) {
      val canExecute = when (filePermissionBranch) {
        OWNER -> fileInfo.permissions.ownerCanExecute
        GROUP -> fileInfo.permissions.groupCanExecute
        OTHER -> fileInfo.permissions.otherCanExecute
      }
      if (!canExecute) {
        return AccessMode.EXECUTE
      }
    }
    return null
  }

  /**
   * returns one of [modes] which is not satisfied, or `null` if all modes are satisfied
   */
  @ApiStatus.Internal
  fun checkAccess(fileInfo: EelWindowsFileInfo, vararg modes: AccessMode): AccessMode? {
    // TODO check other permissions
    if (AccessMode.WRITE in modes) {
      val canWrite = !fileInfo.permissions.isReadOnly
      if (!canWrite) {
        return AccessMode.WRITE
      }
    }
    return null
  }

  fun deleteRecursively(path: Path) {
    // TODO optimize the remote FS case
    FileUtilRt.deleteRecursively(path, null)
  }
}

/**
 * Create [Path] from [pathOnEel] on [eel]
 * Same as Java [Path.of] but supports paths on eels
 */
@ApiStatus.Experimental
@Throws(EelPathException::class)
fun Path(pathOnEel: String, eel: EelDescriptor): Path = EelPath.parse(pathOnEel, eel).asNioPath()