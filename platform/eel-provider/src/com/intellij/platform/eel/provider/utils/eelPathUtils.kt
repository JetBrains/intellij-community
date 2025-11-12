// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder
import com.intellij.platform.eel.fs.EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.*
import com.intellij.platform.eel.provider.utils.EelPathUtils.UnixFilePermissionBranch.*
import com.intellij.platform.eel.provider.utils.EelPathUtils.incrementalWalkingTransfer
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.sync.Semaphore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessMode
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.FileAlreadyExistsException
import kotlin.io.path.*
import kotlin.io.path.Path
import kotlin.math.min

@ApiStatus.Internal
object EelPathUtils {
  private val LOG = com.intellij.openapi.diagnostic.logger<EelPathUtils>()

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
    val userHome = eelDescriptor.toEelApiBlocking().userInfo.home
    val path = runCatching { Path(path).asEelPath().toString() }.getOrNull() ?: path // try to normalize path

    return eelDescriptor.getPath(if (path == "~") {
      userHome.toString()
    } else if (path.startsWith("~/") || path.startsWith("~\\")) {
      userHome.toString() + path.substring(1)
    } else {
      path
    }).asNioPath()
  }

  /**
   * **Obsolete – avoid it in new code.**
   *
   * Although positioned one level higher than *isPathLocal*, this helper still bakes in the same
   * local-vs-remote distinction, tying callers to a specific execution environment and reducing the
   * portability of higher-level logic.
   *
   * @return `true` when the project's default Eel target is local.
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun isProjectLocal(project: Project): Boolean {
    val projectFilePath = project.projectFilePath ?: return true
    return isPathLocal(Path.of(projectFilePath))
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryFile(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = true): Path {
    if (project == null || isProjectLocal(project)) {
      return Files.createTempFile(prefix, suffix)
    }
    val projectFilePath = project.projectFilePath ?: return Files.createTempFile(prefix, suffix)
    return runBlockingMaybeCancellable {
      val eel = Path.of(projectFilePath).getEelDescriptor().toEelApi()
      val file = eel.fs.createTemporaryFile().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).getOrThrowFileSystemException()
      file.asNioPath()
    }
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(project: Project): Path {
    return getSystemFolder(project.getEelDescriptor().toEelApiBlocking())
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(eelDescriptor: EelDescriptor): Path {
    return getSystemFolder(eelDescriptor.toEelApiBlocking())
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(eel: EelApi): Path {
    val selector = PathManager.getPathsSelector() ?: "IJ-Platform"
    val userHomeFolder = eel.userInfo.home.asNioPath().toString()
    return Path.of(PathManager.getDefaultSystemPathFor(eel.platform.toOs(), userHomeFolder, selector, eel.exec.fetchLoginShellEnvVariablesBlocking()))
  }

  @JvmStatic
  @OptIn(ExperimentalPathApi::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryDirectory(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    if (project == null || isProjectLocal(project) || project.projectFilePath == null) {
      val dir = Files.createTempDirectory(prefix)
      if (deleteOnExit) {
        Runtime.getRuntime().addShutdownHook(Thread {
          dir.deleteRecursively()
        })
      }
      return dir
    }
    val eel = Path.of(project.projectFilePath!!).getEelDescriptor().toEelApiBlocking()
    return createTemporaryDirectory(eel, prefix, suffix, deleteOnExit)
  }

  private suspend fun createTemporaryDirectoryImpl(eelApi: EelApi, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    val file = eelApi.fs.createTemporaryDirectory()
      .prefix(prefix)
      .suffix(suffix)
      .deleteOnExit(deleteOnExit)
      .getOrThrowFileSystemException()
    return file.asNioPath()
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryDirectory(eelApi: EelApi, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    return runBlockingMaybeCancellable { createTemporaryDirectoryImpl(eelApi, prefix, suffix, deleteOnExit) }
  }

  @JvmStatic
  fun getNioPath(path: String, descriptor: EelDescriptor): Path {
    return EelPath.parse(path, descriptor).asNioPath()
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun renderAsEelPath(path: Path): String {
    val eelPath = path.asEelPath()
    if (eelPath.descriptor == LocalEelDescriptor) {
      return path.toString()
    }
    return runBlockingMaybeCancellable {
      eelPath.toString()
    }
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
  @RequiresBackgroundThread
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
  @RequiresBackgroundThread
  fun transferLocalContentToRemote(
    source: Path,
    target: TransferTarget,
    fileAttributesStrategy: FileTransferAttributesStrategy = FileTransferAttributesStrategy.Copy,
  ): Path {
    if (source.getEelDescriptor() !== LocalEelDescriptor) {
      return source
    }

    return when (target) {
      is TransferTarget.Temporary -> {
        if (target.descriptor === LocalEelDescriptor) {
          return source
        }

        runBlockingMaybeCancellable {
          service<TransferredContentHolder>().transferIfNeeded(target.descriptor.toEelApi(), source, fileAttributesStrategy)
        }
      }
      is TransferTarget.Explicit -> {
        val sink = target.path

        if (source.isDirectory()) { // todo: use checksums for directories?
          if (!Files.exists(sink)) {
            walkingTransfer(source, sink, false, fileAttributesStrategy)
          }

          sink
        }
        else {
          val remoteHash = if (Files.exists(sink)) calculateFileHashUsingPartialContent(sink) else ""
          val sourceHash = calculateFileHashUsingPartialContent(source)

          if (sourceHash != remoteHash) {
            val tempSink = sink.resolveSibling(sink.fileName.toString() + ".part")

            try {
              walkingTransfer(source, tempSink, false, fileAttributesStrategy)
              Files.move(tempSink, sink, StandardCopyOption.REPLACE_EXISTING)
            }
            finally {
              // This file can only exist if `Files.move` hasn't been called
              Files.deleteIfExists(tempSink)
            }
          }

          sink
        }
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
  @Service
  private class TransferredContentHolder(private val scope: CoroutineScope) {

    data class CacheKey(
      val sourcePathString: String,
      val fileAttributesStrategy: FileTransferAttributesStrategy,
    )

    data class CacheValue(
      val sourceHash: String,
      val transferredFilePath: Path
    )

    private class Cache: ConcurrentHashMap<CacheKey, Deferred<CacheValue>>()

    // eel api instance -> (source path string -> source hash -> transferred file)
    private val caches = CollectionFactory.createConcurrentWeakIdentityMap<EelApi, Cache>()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun transferIfNeeded(eel: EelApi, source: Path, fileAttributesStrategy: FileTransferAttributesStrategy): Path {
      val cache = caches.computeIfAbsent(eel) { Cache() }

      return cache.compute(CacheKey(source.toString(), fileAttributesStrategy)) { _, deferred ->
        val sourceHash by lazy { calculateFileHashUsingMetadata(source) }

        if (deferred != null) {
          if (deferred.isCompleted) {
            val (oldSourceHash, _) = deferred.getCompleted()
            if (oldSourceHash == sourceHash) {
              return@compute deferred
            }
          }
          else {
            return@compute deferred
          }
        }

        scope.async {
          val temp = eel.createTempFor(source, true)
          walkingTransfer(source, temp, false, fileAttributesStrategy)
          CacheValue(sourceHash, temp)
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

  /**
   * Calculates a SHA-256 hash for a given file using only its metadata.
   *
   * This function computes a hash based solely on file attributes:
   * - File size.
   * - Last modified time.
   * - Creation time.
   * - File key (if available).
   * - File permissions (if available).
   *
   * @param path the file path for which the hash is calculated.
   * @return a hexadecimal string representing the computed SHA-256 hash.
   */
  private fun calculateFileHashUsingMetadata(path: Path): String {
    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
    val fileSize = attributes.size()
    val lastModified = attributes.lastModifiedTime().toMillis()
    val creationTime = attributes.creationTime().toMillis()
    val fileKey = attributes.fileKey()?.toString() ?: ""

    val permissions = if (attributes is PosixFileAttributes) {
      val sb = StringBuilder()
      sb.append(attributes.group().name)
      sb.append("\\0")
      sb.append(attributes.owner().name)
      for (permission in attributes.permissions()) {
        sb.append(permission.name)
      }
      sb.toString()
    }
    else {
      ""
    }

    val digest = MessageDigest.getInstance("SHA-256")

    digest.update(fileSize.toString().toByteArray())
    digest.update(lastModified.toString().toByteArray())
    digest.update(creationTime.toString().toByteArray())
    digest.update(fileKey.toByteArray())
    digest.update(permissions.toByteArray())

    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Calculates a SHA-256 hash for a given file by reading only selected parts of its content.
   *
   * This function computes a hash by updating a SHA-256 digest with:
   * - The file size (converted to a string and then to bytes).
   * - Partial content of the file read in 1 MB chunks:
   *   - **Small files (<= 2 MB):** the entire file content is read.
   *   - **Medium files (<= 3 MB):** only the first and last 1 MB chunks are read.
   *   - **Large files (> 3 MB):** three chunks are read – the first, middle, and last 1 MB segments.
   *
   * This selective reading strategy minimizes I/O overhead while capturing key parts of the file.
   *
   * @param path the file path for which the hash is calculated.
   * @return a hexadecimal string representing the computed SHA-256 hash.
   */
  private fun calculateFileHashUsingPartialContent(path: Path): String {
    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
    val fileSize = attributes.size()
    val digest = MessageDigest.getInstance("SHA-256")

    digest.update(fileSize.toString().toByteArray())

    // (1 MB)
    val chunkSize = 1024 * 1024L

    fun readFully(channel: FileChannel, buffer: ByteBuffer, startPosition: Long) {
      var pos = startPosition
      while (buffer.hasRemaining()) {
        val bytesRead = channel.read(buffer, pos)
        if (bytesRead <= 0) break
        pos += bytesRead
      }
    }

    FileChannel.open(path, READ).use { channel ->
      when {
        fileSize <= chunkSize * 2 -> { // For small files, read the entire file content
          val buffer = ByteBuffer.allocate(fileSize.toInt())
          readFully(channel, buffer, 0)
          buffer.flip()
          digest.update(buffer)
        }
        fileSize <= chunkSize * 3 -> { // For medium files, read first and last chunks
          val buffer = ByteBuffer.allocate(chunkSize.toInt())

          // Read the first chunk
          readFully(channel, buffer, 0)
          buffer.flip()
          digest.update(buffer)

          // Read the last chunk
          buffer.clear()
          readFully(channel, buffer, fileSize - chunkSize)
          buffer.flip()
          digest.update(buffer)
        }
        else -> { // For large files, read first, middle, and last chunks
          val buffer = ByteBuffer.allocate(chunkSize.toInt())

          // Read the first chunk
          readFully(channel, buffer, 0)
          buffer.flip()
          digest.update(buffer)

          // Read the middle chunk
          buffer.clear()
          val middlePosition = fileSize / 2 - chunkSize / 2
          readFully(channel, buffer, middlePosition)
          buffer.flip()
          digest.update(buffer)

          // Read the last chunk
          buffer.clear()
          readFully(channel, buffer, fileSize - chunkSize)
          buffer.flip()
          digest.update(buffer)
        }
      }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
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

  @ApiStatus.Internal
  @RequiresBackgroundThread
  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, copyAttributes: Boolean) {
    val fileAttributesStrategy = if (copyAttributes) FileTransferAttributesStrategy.Copy else FileTransferAttributesStrategy.Skip
    return walkingTransfer(sourceRoot, targetRoot, removeSource, fileAttributesStrategy)
  }

  sealed interface FileTransferAttributesStrategy {
    object Skip : FileTransferAttributesStrategy

    interface SourceAware : FileTransferAttributesStrategy {
      fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes)
    }

    object Copy : SourceAware {
      override fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
        copyAttributes(sourceAttrs, target, sourcePathString = source.pathString)
      }
    }

    data class RequirePosixPermissions(val requiredPermissions: Set<PosixFilePermission>) : SourceAware {
      override fun handleFileAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
        copyAttributes(sourceAttrs, target, sourcePathString = source.pathString,
                       requirePosixPermissions = requiredPermissions)
      }
    }

    companion object {
      @JvmStatic
      fun copyWithRequiredPosixPermissions(vararg requiredPermissions: PosixFilePermission): RequirePosixPermissions =
        RequirePosixPermissions(setOf(elements = requiredPermissions))
    }
  }

  @RequiresBackgroundThread
  @RequiresBlockingContext
  @VisibleForTesting
  fun walkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    fileAttributesStrategy: FileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler? = null,
  ) {
    if (Registry.`is`("ijent.incremental.walking.transfer") && !removeSource) {
      runBlockingMaybeCancellable {
        incrementalWalkingTransfer(sourceRoot, targetRoot, fileAttributesStrategy, absoluteSymlinkHandler)
      }
      return
    }

    val shouldObtainExtendedAttributes = when (fileAttributesStrategy) {
      FileTransferAttributesStrategy.Skip -> false
      is FileTransferAttributesStrategy.SourceAware -> true
    }

    fun processFileAttributesOrSkip(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
      when (fileAttributesStrategy) {
        FileTransferAttributesStrategy.Skip -> Unit
        is FileTransferAttributesStrategy.SourceAware -> fileAttributesStrategy.handleFileAttributes(source, target, sourceAttrs)
      }
    }

    val traversalStack = ArrayDeque<TraversalRecord>()
    traversalStack.add(TraversalRecord.Pending(sourceRoot, isRoot = true))

    while (true) {
      val currentTraverseItem = try {
        traversalStack.removeLast()
      }
      catch (_: NoSuchElementException) {
        break
      }
      when (currentTraverseItem) {
        is TraversalRecord.Pending -> {
          val source = currentTraverseItem.sourcePath
          // WindowsPath doesn't support resolve() from paths of different class.
          val target = source.relativeTo(sourceRoot).fold(targetRoot) { parent, file ->
            parent.resolve(file.toString())
          }

          val sourceAttrs: BasicFileAttributes = readSourceAttrs(source, target, withExtendedAttributes = shouldObtainExtendedAttributes)

          when {
            sourceAttrs.isDirectory -> {
              traversalStack.add(currentTraverseItem.asListedDirectory(target, sourceAttrs))
              try {
                target.createDirectories()
              }
              catch (err: FileAlreadyExistsException) {
                if (!Files.isDirectory(target)) {
                  throw err
                }
              }
              source.fileSystem.provider().newDirectoryStream(source) { true }.use { children ->
                traversalStack.addAll(children.toList().asReversed().map { TraversalRecord.Pending(it) })
              }
            }

            sourceAttrs.isRegularFile -> {
              Files.newInputStream(source, READ).use { reader ->
                Files.newOutputStream(target, CREATE, TRUNCATE_EXISTING, WRITE).use { writer ->
                  reader.copyTo(writer, bufferSize = 4 * 1024 * 1024)
                }
              }
              if (removeSource) {
                Files.delete(source)
              }
              processFileAttributesOrSkip(source, target, sourceAttrs)
            }

            sourceAttrs.isSymbolicLink -> {
              Files.copy(source, target, LinkOption.NOFOLLOW_LINKS)
              processFileAttributesOrSkip(source, target, sourceAttrs)
              if (removeSource) {
                Files.delete(source)
              }
            }

            else -> {
              LOG.info("Not copying $source to $target because the source file is neither a regular file nor a directory")
              if (removeSource) {
                Files.delete(source)
              }
            }
          }
        }
        is TraversalRecord.ListedDirectory -> {
          if (!currentTraverseItem.isRoot) {
            if (removeSource) {
              Files.delete(currentTraverseItem.sourcePath)
            }
            processFileAttributesOrSkip(currentTraverseItem.sourcePath, currentTraverseItem.targetPath, currentTraverseItem.sourceAttrs)
          }
        }
      }
    }
  }


  /**
   * Compare individual parts of two relative paths lexicographically.
   * Case-sensitive by default.
   * A shorter component is considered lower compared to a longer component.
   * Example: "a/b" < "ab/b" == True
   */
  private fun compareRelativePathComponents(left: Path, right: Path, ignoreCase: Boolean = false): Int {
    val left = left
      .toString()
      .replace("\\", "/")
      .split("/")

    val right = right
      .toString()
      .replace("\\", "/")
      .split("/")

    for (i in 0 until min(left.size, right.size)) {
      val result = left[i].compareTo(right[i], ignoreCase)
      if (result != 0) {
        return result
      }
    }

    return left.size.compareTo(right.size)
  }

  // NOTE: in the future it could support windows/posix ACLs
  /**
   * Function only checks permissions, and it ignores the owner, group, sticky bit, gid, and uid.
   * If FileTransferAttributesStrategy is RequirePosixPermissions, it will be checked if remote file permissions contain required permissions.
   **/
  private fun arePermissionsEqual(fileAttributesStrategy: FileTransferAttributesStrategy, local: WalkDirectoryEntry.Permissions, remote: WalkDirectoryEntry.Permissions): Boolean {
    return when (local) {
      is WalkDirectoryEntryPosix.Permissions -> {
        when (remote) {
          is WalkDirectoryEntryPosix.Permissions -> {
            if (fileAttributesStrategy is FileTransferAttributesStrategy.RequirePosixPermissions) {
              (local.permissionsSet + fileAttributesStrategy.requiredPermissions) == remote.permissionsSet
            }
            else {
              local.permissionsSet == remote.permissionsSet
            }
          }
          is WalkDirectoryEntryWindows.Permissions -> {
            true
          }
        }
      }
      else -> {
        when (remote) {
          is WalkDirectoryEntryPosix.Permissions -> {
            fileAttributesStrategy !is FileTransferAttributesStrategy.RequirePosixPermissions
          }
          is WalkDirectoryEntryWindows.Permissions -> {
            true
          }
        }
      }
    }
  }

  /**
   * The presence of file timestamps varies by OS and FS. Thus, when comparing timestamps, if a timestamp does not exist on one side,
   * it should not be treated as a mismatch. Creation/birth timestamps are ignored as they cannot be set on POSIX.
   **/
  private fun areTimestampsEqual(left: WalkDirectoryEntry, right: WalkDirectoryEntry): Boolean {
    val isAccessTimeEqual = left.lastAccessTime?.let { localTimestamp ->
      right.lastAccessTime?.let { remoteTimestamp ->
        localTimestamp.compareTo(remoteTimestamp) == 0
      }
    } ?: true

    val isModifiedTimeEqual = left.lastModifiedTime?.let { localTimestamp ->
      right.lastModifiedTime?.let { remoteTimestamp ->
        localTimestamp.compareTo(remoteTimestamp) == 0
      }
    } ?: true

    // the creation time of a file on posix cannot be set, so if one of the paths is posix, it's treated as equal
    val isCreationTimeEqual = if (left.path.descriptor.osFamily.isPosix || right.path.descriptor.osFamily.isPosix || left.creationTime == null || right.creationTime == null) {
      true
    }
    else {
      left.creationTime?.let { localTimestamp ->
        right.creationTime?.let { remoteTimestamp ->
          localTimestamp.compareTo(remoteTimestamp) == 0
        }
      } ?: true
    }

    return isAccessTimeEqual && isModifiedTimeEqual && isCreationTimeEqual
  }

  // NOTE: in the future it could support file system specific file attributes
  private fun areAttributesEqual(left: WalkDirectoryEntry, right: WalkDirectoryEntry): Boolean {
    return when (left) {
      is WalkDirectoryEntryWindows -> when (right) {
        is WalkDirectoryEntryPosix -> {
          true
        }
        is WalkDirectoryEntryWindows -> {
          left.attributes == right.attributes
        }
      }
      else -> {
        true
      }
    }
  }

  sealed class DiffOperation() {
    // Always syncs permissions, attributes, and timestamps.
    data class Create(
      val localFile: WalkDirectoryEntry,
    ) : DiffOperation()

    data class Delete(
      val remoteFile: WalkDirectoryEntry,
    ) : DiffOperation()

    data class UpdateMetadata(
      val updatePermissions: Boolean = false,
      val updateAttributes: Boolean = false,
      val updateTimestamps: Boolean = false,
      val localFile: WalkDirectoryEntry,
      val remoteFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // If a file has the same local and remote path but different file type, the existing one is deleted and replaced with a correct one.
    // ReplaceFile is additionally used in the case of a relative symlink. The symlink target cannot be changed in place, thus it requires replacing.
    // Always syncs permission, attributes and timestamps.
    data class ReplaceFile(
      val localFile: WalkDirectoryEntry,
      val remoteFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // Always updates timestamps
    data class UpdateContents(
      val updatePermissions: Boolean = false,
      val updateAttributes: Boolean = false,
      val updateTimestamps: Boolean = false,
      val localFile: WalkDirectoryEntry,
      val remoteFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // Absolute symlinks are left untouched, and it is up to the user-provided lambda to handle it
    data class AbsoluteSymlink(
      val sourceSymlink: WalkDirectoryEntry,
      val remoteSymlink: WalkDirectoryEntry?,
    ) : DiffOperation()
  }

  /**
   * Function that creates a flow which combines local and remote file information.
   * Flow emits [DiffOperation]s that indicate what has to be done to the remote file to make it be in sync with the local file.
   **/
  fun mergeHashByPath(
    scope: CoroutineScope,
    localEntryPoint: Path,
    remoteEntryPoint: Path,
    localHashFlow: Flow<WalkDirectoryEntryResult>,
    remoteHashFlow: Flow<WalkDirectoryEntryResult>,
    fileAttributesStrategy: FileTransferAttributesStrategy,
    ignoreCase: Boolean,
  ): Flow<DiffOperation> = flow {
    val localHashChan = localHashFlow.produceIn(scope)
    val remoteHashChan = remoteHashFlow.produceIn(scope)

    var localEntryResult: WalkDirectoryEntryResult? = null
    var localEntry: WalkDirectoryEntry? = null
    var remoteEntryResult: WalkDirectoryEntryResult? = null
    var remoteEntry: WalkDirectoryEntry? = null

    while (true) {
      if (localEntryResult == null) {
        try {
          localEntryResult = localHashChan.receive()
        }
        catch (_: ClosedReceiveChannelException) {
        }
      }

      if (remoteEntryResult == null) {
        try {
          remoteEntryResult = remoteHashChan.receive()
        }
        catch (_: ClosedReceiveChannelException) {
        }
      }

      if (remoteEntryResult != null) {
        remoteEntry = when (remoteEntryResult) {
          is WalkDirectoryEntryResult.Ok -> remoteEntryResult.value
          is WalkDirectoryEntryResult.Error -> {
            LOG.info("Error getting entry on the remote side: ${remoteEntryResult.error}")
            break
          }
        }
      }

      if (localEntryResult != null) {
        localEntry = when (localEntryResult) {
          is WalkDirectoryEntryResult.Ok -> localEntryResult.value
          is WalkDirectoryEntryResult.Error -> {
            LOG.info("Error getting entry on the local side: ${localEntryResult.error}")
            break
          }
        }
      }

      LOG.trace("Merge hash by path comparing source: $localEntry to target: $remoteEntry")
      if (localEntry == null && remoteEntry == null) {
        break
      }

      // if there is no file locally but there is a file on the remote side - the file was deleted
      if (localEntry == null && remoteEntry != null) {
        if (remoteEntry.type !is WalkDirectoryEntry.Type.Other) {
          emit(DiffOperation.Delete(remoteEntry))
        }
        remoteEntry = null
        remoteEntryResult = null
        continue
      }

      // if there is a file locally but not on the remote side - the file was created
      if (localEntry != null && remoteEntry == null) {
        if (localEntry.type is WalkDirectoryEntry.Type.Symlink.Absolute) {
          emit(DiffOperation.AbsoluteSymlink(localEntry, null))
        }
        else if (localEntry.type !is WalkDirectoryEntry.Type.Other) {
          emit(DiffOperation.Create(localEntry))
        }
        localEntry = null
        localEntryResult = null
        continue
      }

      // ignore other file types
      if (localEntry!!.type is WalkDirectoryEntry.Type.Other) {
        localEntry = null
        localEntryResult = null
        continue
      }

      if (remoteEntry!!.type is WalkDirectoryEntry.Type.Other) {
        remoteEntry = null
        remoteEntryResult = null
        continue
      }

      val relativeLocalPath = localEntry.path.asNioPath().relativeTo(localEntryPoint)
      val relativeRemotePath = remoteEntry.path.asNioPath().relativeTo(remoteEntryPoint)

      val pathComparison = compareRelativePathComponents(relativeLocalPath, relativeRemotePath, ignoreCase)

      // if the same file is present on both sides, and if the permissions/hash/type is different, sync them
      if (pathComparison == 0) {
        val updatePermissions = fileAttributesStrategy !is FileTransferAttributesStrategy.Skip && !arePermissionsEqual(fileAttributesStrategy, localEntry.permissions!!, remoteEntry.permissions!!)
        val updateAttributes = fileAttributesStrategy !is FileTransferAttributesStrategy.Skip && !areAttributesEqual(localEntry, remoteEntry)
        val updateTimestamps = fileAttributesStrategy !is FileTransferAttributesStrategy.Skip && !areTimestampsEqual(localEntry, remoteEntry)
        var opEmitted = false

        when (localEntry.type) {
          is WalkDirectoryEntry.Type.Directory -> {
            if (remoteEntry.type !is WalkDirectoryEntry.Type.Directory) {
              emit(DiffOperation.ReplaceFile(localEntry, remoteEntry))
              opEmitted = true
            }
          }
          is WalkDirectoryEntry.Type.Regular -> {
            if (remoteEntry.type is WalkDirectoryEntry.Type.Regular) {
              if ((localEntry.type as WalkDirectoryEntry.Type.Regular).hash != (remoteEntry.type as WalkDirectoryEntry.Type.Regular).hash) {
                // updating file contents implies updating modification timestamp
                emit(DiffOperation.UpdateContents(updatePermissions, updateAttributes, true, localEntry, remoteEntry))
                opEmitted = true
              }
            }
            else {
              emit(DiffOperation.ReplaceFile(localEntry, remoteEntry))
              opEmitted = true
            }
          }
          is WalkDirectoryEntry.Type.Symlink.Relative -> {
            if (remoteEntry.type is WalkDirectoryEntry.Type.Symlink.Relative) {
              val localPathInPosix = (localEntry.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath.replace("\\", "/")
              val remotePathInPosix = (remoteEntry.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath.replace("\\", "/")
              if (localPathInPosix != remotePathInPosix) {
                emit(DiffOperation.ReplaceFile(localEntry, remoteEntry))
                opEmitted = true
              }
            }
            else {
              emit(DiffOperation.ReplaceFile(localEntry, remoteEntry))
              opEmitted = true
            }
          }
          is WalkDirectoryEntry.Type.Symlink.Absolute -> {
            emit(DiffOperation.AbsoluteSymlink(localEntry, remoteEntry))
            opEmitted = true
          }
          is WalkDirectoryEntry.Type.Other -> {
            // other file types have been handled prior to this when
          }
        }

        // if no other op has been emitted, check if metadata is equal
        // ignore differences in permissions on symlink, as they are ignored
        if (!opEmitted && (updatePermissions || updateAttributes || updateTimestamps) && ((localEntry.type !is WalkDirectoryEntry.Type.Symlink) || (localEntry.type is WalkDirectoryEntry.Type.Symlink && (updateAttributes || updateTimestamps)))) {
          emit(DiffOperation.UpdateMetadata(
            updatePermissions = updatePermissions,
            updateAttributes = updateAttributes,
            updateTimestamps = updateTimestamps,
            localFile = localEntry,
            remoteFile = remoteEntry,
          ))
        }

        localEntry = null
        localEntryResult = null
        remoteEntry = null
        remoteEntryResult = null
      }
      // if the local path is in lower lexicographical order than the remote path, it means that the local file was created
      else if (pathComparison < 0) {
        if (localEntry is WalkDirectoryEntry.Type.Symlink.Absolute) {
          emit(DiffOperation.AbsoluteSymlink(localEntry, null))
        }
        else {
          emit(DiffOperation.Create(localEntry))
        }
        localEntry = null
        localEntryResult = null
      }
      // if the local path is higher in lexicographical order than the remote path, it means that the remote file was deleted
      else {
        emit(DiffOperation.Delete(remoteEntry))
        remoteEntry = null
        remoteEntryResult = null
      }
    }
  }

  private fun convertPermissionsToMask(permissions: Set<PosixFilePermission>): Int {
    var mask = 0
    if (PosixFilePermission.OWNER_READ in permissions) mask = mask or 0b100000000
    if (PosixFilePermission.OWNER_WRITE in permissions) mask = mask or 0b010000000
    if (PosixFilePermission.OWNER_EXECUTE in permissions) mask = mask or 0b001000000
    if (PosixFilePermission.GROUP_READ in permissions) mask = mask or 0b000100000
    if (PosixFilePermission.GROUP_WRITE in permissions) mask = mask or 0b000010000
    if (PosixFilePermission.GROUP_EXECUTE in permissions) mask = mask or 0b000001000
    if (PosixFilePermission.OTHERS_READ in permissions) mask = mask or 0b000000100
    if (PosixFilePermission.OTHERS_WRITE in permissions) mask = mask or 0b000000010
    if (PosixFilePermission.OTHERS_EXECUTE in permissions) mask = mask or 0b000000001
    return mask
  }

  private suspend fun setPermissionsAndAttributes(
    localEntry: WalkDirectoryEntry,
    remoteEntry: Path,
    remoteEelApi: EelApi,
    fileAttributesStrategy: FileTransferAttributesStrategy,
    setPermissions: Boolean,
    setAttributes: Boolean,
    setTimestamps: Boolean,
  ) {
    val attributesOptions = ChangeAttributesOptionsBuilder(remoteEntry.asEelPath())

    if (setPermissions) {
      remoteEntry.fileAttributesViewOrNull<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { remoteView ->
        val perms = mutableSetOf<PosixFilePermission>()

        val requiredPerms = if (fileAttributesStrategy is FileTransferAttributesStrategy.RequirePosixPermissions) {
          fileAttributesStrategy.requiredPermissions
        }
        else {
          setOf()
        }
        perms.addAll(requiredPerms)

        // NOTE: changing attributes through IJent does not change the owner and the group of a file, so it can be left as zero
        var owner = 0
        var group = 0

        if (localEntry.permissions == null) {
          error("Permissions are supposed to be transferred, but were not yielded")
        }
        when (localEntry) {
          is WalkDirectoryEntryPosix -> {
            perms.addAll(localEntry.permissions!!.permissionsSet)
            owner = localEntry.permissions!!.owner
            group = localEntry.permissions!!.group
          }
          is WalkDirectoryEntryWindows -> {
            perms.addAll(remoteView.readAttributes().permissions())
          }
        }
        attributesOptions.permissions(EelPosixFileInfoImpl.Permissions(owner, group, convertPermissionsToMask(perms)))
      }
    }

    if (setAttributes) {
      if (localEntry.attributes == null) {
        error("Attributes are supposed to be transferred, but were not yielded")
      }
      remoteEntry.fileAttributesViewOrNull<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { remoteView ->
        when (localEntry) {
          is WalkDirectoryEntryPosix -> {}
          is WalkDirectoryEntryWindows -> {
            remoteView.setHidden(localEntry.attributes!!.isHidden)
            remoteView.setSystem(localEntry.attributes!!.isSystem)
            remoteView.setArchive(localEntry.attributes!!.isArchive)
            remoteView.setReadOnly(localEntry.attributes!!.isReadOnly)
          }
        }
      }
    }

    if (setTimestamps) {
      localEntry.lastModifiedTime?.let { time ->
        val time = time.toInstant()
        val epoch = EelFileSystemApi.timeSinceEpoch(time.epochSecond.toULong(), time.nano.toUInt())
        attributesOptions.modificationTime(epoch)
      }

      localEntry.lastAccessTime?.let { time ->
        val time = time.toInstant()
        val epoch = EelFileSystemApi.timeSinceEpoch(time.epochSecond.toULong(), time.nano.toUInt())
        attributesOptions.accessTime(epoch)
      }
    }

    remoteEelApi.fs.changeAttributes(attributesOptions.build())
  }

  /**
   * Synchronizes the remote directory tree with the local one (directories only).
   * This extra pass is necessary to handle:
   *   - Races when creating parent directories for files
   *   - An edge case where a source directory is deleted and replaced by a file with the same name
   *
   * It also reduces redundant system calls when creating files.
   * @param sourceRoot Has to be a valid path to a directory
   * @param targetRoot Has to be a valid path to a directory
   */
  @RequiresBackgroundThread
  @VisibleForTesting
  suspend fun directoryOnlySync(
    sourceRoot: EelPath,
    targetRoot: EelPath,
    targetEelApi: EelApi,
    ignoreCase: Boolean,
  ) {
    val sourceRoot = sourceRoot.asNioPath()
    val targetRoot = targetRoot.asNioPath()
    val localQ = ArrayDeque<Path>()
    val remoteQ = ArrayDeque<Path>()
    localQ.add(sourceRoot)
    remoteQ.add(targetRoot)

    while (localQ.isNotEmpty() || remoteQ.isNotEmpty()) {
      if (localQ.isNotEmpty() && remoteQ.isEmpty()) {
        val path = localQ.removeFirst()
        val relativeDirPath = path.relativeTo(sourceRoot)
        val targetDirPath = targetRoot.resolve(relativeDirPath)

        // edge case when a local directory is deleted and replaced by a file with the same name
        if (targetDirPath.exists()) {
          Files.delete(targetDirPath)
        }
        Files.createDirectory(targetDirPath)
        localQ.addAll(
          path.listDirectoryEntries()
            .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
            .sortedByDescending { it.pathString }
        )
      }
      else if (localQ.isEmpty() && remoteQ.isNotEmpty()) {
        val path = remoteQ.removeFirst()
        targetEelApi.fs.delete(path.asEelPath(), true)
      }
      else {
        val localRelativeDirPath = localQ.first().relativeTo(sourceRoot)
        val remoteRelativeDirPath = remoteQ.first().relativeTo(targetRoot)
        val comparison = compareRelativePathComponents(localRelativeDirPath, remoteRelativeDirPath, ignoreCase)

        // new local directory
        if (comparison > 0) {
          val dirTargetPath = targetRoot.resolve(localRelativeDirPath)

          // edge case when a local directory is deleted and replaced by a file with the same name
          if (dirTargetPath.exists()) {
            Files.delete(dirTargetPath)
          }
          Files.createDirectory(dirTargetPath)
          localQ.removeFirst()
        }
        // the local directory was deleted
        else if (comparison < 0) {
          targetEelApi.fs.delete(remoteQ.removeFirst().asEelPath(), true).getOrThrow()
        }
        else {
          val localPath = localQ.removeFirst()
          val remotePath = remoteQ.removeFirst()
          localQ.addAll(
            localPath.listDirectoryEntries()
              .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
              .sortedByDescending { it.pathString }
          )
          remoteQ.addAll(
            remotePath.listDirectoryEntries()
              .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
              .sortedByDescending { it.pathString }
          )
        }
      }
    }
  }

  /**
   * Callback invoked for each absolute symlink encountered during transfer.
   * The lambda can do whatever it wants: recreate the symlink, copy contents,
   * ignore it, call [incrementalWalkingTransfer] again on it, or implement any custom logic.
   */
  fun interface IncrementalWalkingTransferAbsoluteSymlinkHandler {
    /**
     * @param sourceSymlink Information about the source symlink
     * @param targetEntry Directory entry that lives on the path where the source symlink should be. May or may not be a symlink.
     */
    suspend fun handle(sourceSymlink: WalkDirectoryEntry, targetEntry: WalkDirectoryEntry?)
  }

  /**
   * Supports transferring directories, files, and symlinks. On POSIX permissions and timestamps are transferred as well if indicated
   * using [FileTransferAttributesStrategy]. Relative symlinks are transferred as is, and the target path does not have to be valid.
   * Permissions on symlinks are not transferred as they are ignored by on Unix-like systems.
   */
  @RequiresBackgroundThread
  private suspend fun incrementalWalkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    fileAttributesStrategy: FileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
  ) {
    coroutineScope {
      val targetRootEel = targetRoot.asEelPath()
      val remoteDescriptor = targetRootEel.descriptor
      val remoteEelApi = remoteDescriptor.toEelApi()
      val localPathEel = sourceRoot.asEelPath()
      val localOsFamily = localPathEel.descriptor.osFamily
      val remoteOsFamily = targetRoot.getEelDescriptor().osFamily

      val sourceAttrs = sourceRoot.fileAttributesView<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
      val targetAttrs = targetRoot.fileAttributesView<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
      when {
        sourceAttrs.isDirectory -> {
          if (!targetAttrs.isDirectory) {
            remoteEelApi.fs.delete(targetRootEel, true).getOrThrow()
            Files.createDirectory(targetRoot)
          }
          withContext(Dispatchers.IO) {
            directoryOnlySync(localPathEel, targetRootEel, remoteDescriptor.toEelApi(), false)
          }
        }
        sourceAttrs.isRegularFile -> {
          if (!targetAttrs.isRegularFile) {
            remoteEelApi.fs.delete(targetRootEel, true).getOrThrow()
            Files.createFile(targetRoot)
          }
        }
        sourceAttrs.isSymbolicLink -> {
          // if targetRoot is a directory, it should be deleted to prevent it from being traversed
          if (targetAttrs.isDirectory) {
            remoteEelApi.fs.delete(targetRootEel, true).getOrThrow()
            // a placeholder file is created so that, in the case of an absolute symlink, the user lambda receives the expected target path (where the absolute symlink should be)
            Files.createFile(targetRoot)
          }
        }
      }

      val walkDirectoryOptionsSource = WalkDirectoryOptionsBuilder(localPathEel)
        .traversalOrder(WalkDirectoryTraversalOrder.DFS)
        .entryOrder(WalkDirectoryEntryOrder.ALPHABETICAL)
        .yieldOtherFileTypes(false)
        .fileContentsHash(true)
        .readMetadata(fileAttributesStrategy !is FileTransferAttributesStrategy.Skip)
        .maxDepth(-1)
        .build()

      val walkDirectoryOptionsTarget = WalkDirectoryOptionsBuilder(targetRoot.asEelPath())
        .traversalOrder(WalkDirectoryTraversalOrder.DFS)
        .entryOrder(WalkDirectoryEntryOrder.ALPHABETICAL)
        .yieldOtherFileTypes(false)
        .fileContentsHash(true)
        .readMetadata(fileAttributesStrategy !is FileTransferAttributesStrategy.Skip)
        .maxDepth(-1)
        .build()
      val localHashes = async(Dispatchers.IO) { localPathEel.descriptor.toEelApi().fs.walkDirectory(walkDirectoryOptionsSource) }
      val remoteHashes = async(Dispatchers.IO) { remoteDescriptor.toEelApi().fs.walkDirectory(walkDirectoryOptionsTarget) }

      val semaphore = Semaphore(4) // TODO: fine tune

      // NOTE: The buffer size was chosen based on benchmark results, which showed that performance improvements leveled off at 64 KiB
      // TODO: different buffer size for larger files
      val bufferSize = 64 * 1024

      mergeHashByPath(this, sourceRoot, targetRoot, localHashes.await(), remoteHashes.await(), fileAttributesStrategy, false).collect { diffOp ->
        // semaphore is used to limit how many files are being synced at any given moment
        semaphore.acquire()
        launch(Dispatchers.IO) {
          try {
            LOG.trace("Applying diff operation: $diffOp")
            when (diffOp) {
              is DiffOperation.Create, is DiffOperation.ReplaceFile -> {
                if (diffOp is DiffOperation.ReplaceFile) Files.delete(diffOp.remoteFile.path.asNioPath())

                val localFile = if (diffOp is DiffOperation.Create) {
                  diffOp.localFile
                }
                else {
                  (diffOp as DiffOperation.ReplaceFile).localFile
                }

                val localFileNioPath = localFile.path.asNioPath()
                val relativePath = localFileNioPath.relativeTo(sourceRoot)
                val remoteAbsolutePath = targetRoot.resolve(relativePath)

                when (localFile.type) {
                  is WalkDirectoryEntry.Type.Directory -> {
                    error("unreachable, directory was supposed to created in a pass before incremental transfer")
                  }
                  is WalkDirectoryEntry.Type.Regular -> {
                    val remoteAbsoluteTempPath = remoteAbsolutePath.resolveSibling(remoteAbsolutePath.fileName.toString() + ".part")
                    try {
                      Files.newInputStream(localFileNioPath, READ).use { localFile ->
                        Files.newOutputStream(remoteAbsoluteTempPath, WRITE, CREATE, TRUNCATE_EXISTING).use { remoteFile ->
                          localFile.copyTo(remoteFile, bufferSize)
                        }
                      }
                      Files.move(remoteAbsoluteTempPath, remoteAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
                      if (fileAttributesStrategy !is FileTransferAttributesStrategy.Skip) {
                        setPermissionsAndAttributes(localFile, remoteAbsolutePath, remoteEelApi, fileAttributesStrategy, true, true, true)
                      }
                    }
                    finally {
                      Files.deleteIfExists(remoteAbsoluteTempPath)
                    }
                  }
                  is WalkDirectoryEntry.Type.Symlink.Relative -> {
                    var symlinkTarget = (localFile.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath
                    if (localOsFamily != remoteOsFamily) {
                      symlinkTarget = if (remoteOsFamily.isWindows) {
                        symlinkTarget.replace("/", "\\")
                      }
                      else {
                        symlinkTarget.replace("\\", "/")
                      }
                    }
                    Files.createSymbolicLink(remoteAbsolutePath, Path(symlinkTarget))
                    // permissions on symlinks are not applied because they are ignored
                    // TODO: setting timestamps on a symlink requires using ffi syscall in ijent
                    //setPermissionsAndAttributes(localFile, remoteAbsolutePath, fileAttributesStrategy, false, true, true)
                  }
                  is WalkDirectoryEntry.Type.Symlink.Absolute -> {
                    error("unreachable, absolute symlink should exclusively be handled by the user provided lambda")
                  }
                  is WalkDirectoryEntry.Type.Other -> {
                    // NOTE: other file types not supported
                  }
                }
              }
              is DiffOperation.Delete -> {
                Files.delete(diffOp.remoteFile.path.asNioPath())
              }
              is DiffOperation.UpdateContents -> {
                val remotePathNio = diffOp.remoteFile.path.asNioPath()
                val tempRemotePath = remotePathNio.resolveSibling(remotePathNio.fileName.toString() + ".part")
                try {
                  Files.newInputStream(diffOp.localFile.path.asNioPath(), READ).use { localFile ->
                    Files.newOutputStream(tempRemotePath, WRITE, TRUNCATE_EXISTING, CREATE).use { remoteFile ->
                      localFile.copyToAsync(remoteFile, bufferSize)
                    }
                  }
                  Files.move(tempRemotePath, remotePathNio, StandardCopyOption.REPLACE_EXISTING)
                  setPermissionsAndAttributes(diffOp.localFile, remotePathNio, remoteEelApi, fileAttributesStrategy, diffOp.updatePermissions, diffOp.updateAttributes, diffOp.updateTimestamps)
                }
                finally {
                  Files.deleteIfExists(tempRemotePath)
                }
              }
              is DiffOperation.UpdateMetadata -> {
                // TODO: setting timestamps on a symlink requires using ffi syscall in ijent
                if (diffOp.localFile.type !is WalkDirectoryEntry.Type.Symlink) {
                  setPermissionsAndAttributes(diffOp.localFile, diffOp.remoteFile.path.asNioPath(), remoteEelApi, fileAttributesStrategy, diffOp.updatePermissions, diffOp.updateAttributes, diffOp.updateTimestamps)
                }
              }
              is DiffOperation.AbsoluteSymlink -> {
                when (absoluteSymlinkHandler) {
                  null -> LOG.info("No absolute symlink handler provided for incremental walking transfer, skipping symlink: ${diffOp.sourceSymlink.path}")
                  else -> absoluteSymlinkHandler.handle(diffOp.sourceSymlink, diffOp.remoteSymlink)
                }
              }
            }
          }
          finally {
            semaphore.release()
          }
        }
      }
    }
  }

  /**
   * Corresponds to a path stored in the stack during the depth-first search traversing the file tree.
   */
  private sealed interface TraversalRecord {
    /**
     * Describes a file or a directory that has been listed and is pending for further processing.
     *
     * Stored in this state in the stack, the corresponding [sourcePath] has neither been copied, nor listed as a directory,
     * nor even had its attributes acquired.
     */
    data class Pending(
      val sourcePath: Path,
      val isRoot: Boolean = false,
    ) : TraversalRecord {
      fun asListedDirectory(targetPath: Path, sourceAttrs: BasicFileAttributes): ListedDirectory =
        ListedDirectory(sourcePath, targetPath, sourceAttrs, isRoot)
    }

    /**
     * Describes a directory with its direct children being listed and put right after this record in the stack.
     *
     * Taking this element from the stack means that all its direct and indirect descendants have been processed,
     * and we are now ready to copy the source directory's attributes and/or remove it.
     */
    data class ListedDirectory(
      val sourcePath: Path,
      val targetPath: Path,
      val sourceAttrs: BasicFileAttributes,
      val isRoot: Boolean = false,
    ) : TraversalRecord
  }

  private fun readSourceAttrs(
    source: Path,
    target: Path,
    withExtendedAttributes: Boolean,
  ): BasicFileAttributes {
    val attributesIntersection =
      if (withExtendedAttributes)
        source.fileSystem.supportedFileAttributeViews() intersect target.fileSystem.supportedFileAttributeViews()
      else
        setOf()

    val osSpecific =
      try {
        when {
          "posix" in attributesIntersection ->
            source.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()

          "dos" in attributesIntersection ->
            source.fileAttributesView<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()

          else -> null
        }
      }
      catch (err: UnsupportedOperationException) {
        LOG.info("Failed to read os-specific file attributes from $source", err)
        null
      }
    return osSpecific ?: source.fileAttributesView<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
  }

  /**
   * Copies file attributes from a source file to the target path, ensuring compatibility with different
   * file systems such as POSIX and Windows.
   *
   * @param sourceAttrs             The file attributes of the source file from which the attributes will be copied.
   * @param target                  The target path where the attributes will be applied.
   * @param sourcePathString        The string representation of the source path, used for logging purposes.
   * @param requirePosixPermissions A set of additional POSIX file permissions required to be merged
   *                                into the copied attributes.
   */
  private fun copyAttributes(
    sourceAttrs: BasicFileAttributes,
    target: Path,
    sourcePathString: String,
    requirePosixPermissions: Set<PosixFilePermission> = emptySet(),
  ) {
    fun <T> Result<T>.logIOExceptionOrThrow(fileAttributeViewName: String): Result<T> =
      handleIOExceptionOrThrow { LOG.info("Failed to copy $fileAttributeViewName file attributes from $sourcePathString to $target: $it") }

    target.fileAttributesViewOrNull<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { posixView ->
      runCatching { copyPosixOnlyFileAttributes(sourceAttrs, posixView, requirePosixPermissions) }
        .logIOExceptionOrThrow(fileAttributeViewName = "Posix")
    }

    target.fileAttributesViewOrNull<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { dosView ->
      runCatching { copyDosOnlyFileAttributes(sourceAttrs, dosView) }
        .logIOExceptionOrThrow(fileAttributeViewName = "Windows")
    }

    target.fileAttributesViewOrNull<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { basicView ->
      runCatching { copyBasicFileAttributes(sourceAttrs, basicView) }
        .logIOExceptionOrThrow(fileAttributeViewName = "basic")
    }
  }

  private fun copyPosixOnlyFileAttributes(
    from: BasicFileAttributes,
    to: PosixFileAttributeView,
    requirePermissions: Set<PosixFilePermission> = emptySet(),
  ) {
    if (from is PosixFileAttributes) {
      // TODO It's ineffective for IjentNioFS, because there are 6 consequential system calls.
      to.setPermissions(from.permissions() + requirePermissions)
      if (to is PosixFileAttributes) {
        runCatching<UnsupportedOperationException>(
          { to.owner = from.owner() },
          { to.setGroup(from.group()) }
        )
      }
    }
    else {
      if (requirePermissions.isNotEmpty()) {
        to.setPermissions(to.readAttributes().permissions() + requirePermissions)
      }
    }
  }

  private fun copyDosOnlyFileAttributes(from: BasicFileAttributes, to: DosFileAttributeView) {
    if (from is DosFileAttributes) {
      to.setHidden(from.isHidden)
      to.setSystem(from.isSystem)
      to.setArchive(from.isArchive)
      to.setReadOnly(from.isReadOnly)
    }
    copyBasicFileAttributes(from, to)
  }

  private fun copyBasicFileAttributes(from: BasicFileAttributes, to: BasicFileAttributeView) {
    to.setTimes(
      from.lastModifiedTime(),
      from.lastAccessTime(),
      from.creationTime(),
    )
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

  fun getCaseSensitivity(directoryType: EelFileInfo.Type.Directory): FileAttributes.CaseSensitivity {
    return when (directoryType.sensitivity) {
      EelFileInfo.CaseSensitivity.SENSITIVE -> FileAttributes.CaseSensitivity.SENSITIVE
      EelFileInfo.CaseSensitivity.INSENSITIVE -> FileAttributes.CaseSensitivity.INSENSITIVE
      EelFileInfo.CaseSensitivity.UNKNOWN -> FileAttributes.CaseSensitivity.UNKNOWN
    }
  }

  fun deleteRecursively(path: Path) {
    // TODO optimize the remote FS case
    NioFiles.deleteRecursively(path)
  }
}

/**
 * Create [Path] from [pathOnEel] on [eel]
 * Same as Java [Path.of] but supports paths on eels
 */
@ApiStatus.Experimental
fun Path(pathOnEel: @NlsSafe String, eel: EelDescriptor): Path = EelPath.parse(pathOnEel, eel).asNioPath()

private inline fun <T> Result<T>.handleIOExceptionOrThrow(action: (exception: IOException) -> Unit): Result<T> =
  onFailure { if (it is IOException) action(it) else throw it }

private inline fun <reified T : Throwable> runCatching(vararg blocks: () -> Unit) {
  blocks.forEach {
    try {
      it()
    }
    catch (t: Throwable) {
      if (!T::class.isInstance(t)) {
        throw t
      }
    }
  }
}
