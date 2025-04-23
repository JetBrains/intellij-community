// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.fileAttributesView
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

@ApiStatus.Internal
object EelPathUtils {
  private val LOG = com.intellij.openapi.diagnostic.logger<EelPathUtils>()

  /**
   * Determines whether [path] correponds to the local Eel
   */
  @JvmStatic
  fun isPathLocal(path: Path): Boolean {
    return path.getEelDescriptor() == LocalEelDescriptor
  }

  @JvmStatic
  fun isProjectLocal(project: Project): Boolean {
    val projectFilePath = project.projectFilePath ?: return true
    return isPathLocal(Path.of(projectFilePath))
  }

  @JvmStatic
  fun createTemporaryFile(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = true): Path {
    if (project == null || isProjectLocal(project)) {
      return Files.createTempFile(prefix, suffix)
    }
    val projectFilePath = project.projectFilePath ?: return Files.createTempFile(prefix, suffix)
    return runBlockingMaybeCancellable {
      val eel = Path.of(projectFilePath).getEelDescriptor().upgrade()
      val file = eel.fs.createTemporaryFile().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).getOrThrowFileSystemException()
      file.asNioPath()
    }
  }

  @JvmStatic
  fun createTemporaryDirectory(project: Project?, prefix: String = ""): Path {
    if (project == null || isProjectLocal(project)) {
      return Files.createTempDirectory(prefix)
    }
    val projectFilePath = project.projectFilePath ?: return Files.createTempDirectory(prefix)
    return runBlockingMaybeCancellable {
      val eel = Path.of(projectFilePath).getEelDescriptor().upgrade()
      createTemporaryDirectory(eel, prefix)
    }
  }

  @JvmStatic
  suspend fun createTemporaryDirectory(eelApi: EelApi, prefix: String = ""): Path {
    val file = eelApi.fs.createTemporaryDirectory().prefix(prefix).getOrThrowFileSystemException()
    return file.asNioPath()
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
    val trailing = if (eelPath.descriptor.platform is EelPlatform.Windows) "/" else ""
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
          service<TransferredContentHolder>().transferIfNeeded(target.descriptor.upgrade(), source, fileAttributesStrategy)
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
              Files.delete(tempSink)
            }
          }

          sink
        }
      }
    }
  }

  @Service
  private class TransferredContentHolder(private val scope: CoroutineScope) {
    // eel descriptor -> source path string ->> source hash -> transferred file
    private val cache = ConcurrentHashMap<Pair<EelDescriptor, String>, Deferred<Pair<String, Path>>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun transferIfNeeded(eel: EelApi, source: Path, fileAttributesStrategy: FileTransferAttributesStrategy): Path {
      return cache.compute(eel.descriptor to source.toString()) { _, deferred ->
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
          sourceHash to temp
        }
      }!!.await().second
    }
  }

  private suspend fun EelApi.createTempFor(source: Path, deleteOnExit: Boolean): Path {
    return if (source.isDirectory()) {
      fs.createTemporaryDirectory().deleteOnExit(deleteOnExit).getOrThrowFileSystemException().asNioPath()
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

    val digest = MessageDigest.getInstance("SHA-256")

    digest.update(fileSize.toString().toByteArray())
    digest.update(lastModified.toString().toByteArray())
    digest.update(creationTime.toString().toByteArray())
    digest.update(fileKey.toByteArray())

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
      descriptor.upgrade()
    }
    val someEelPath = api.fs.user.home
    return someEelPath.asNioPath()
  }

  @Internal
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
  private fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, fileAttributesStrategy: FileTransferAttributesStrategy) {
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
              source.fileSystem.provider().newDirectoryStream(source, { true }).use { children ->
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
      runCatching<UnsupportedOperationException>(
        { to.setOwner(from.owner()) },
        { to.setGroup(from.group()) }
      )
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
}

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