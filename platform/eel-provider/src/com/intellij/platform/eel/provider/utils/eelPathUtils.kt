// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryOptions
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.*
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

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
      val file = eel.fs.createTemporaryFile(CreateTemporaryEntryOptions.Builder().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).build()).getOrThrowFileSystemException()
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
    val file = eelApi.fs.createTemporaryDirectory(CreateTemporaryEntryOptions.Builder().prefix(prefix).build()).getOrThrowFileSystemException()
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
  fun getUriLocalToEel(path: Path): URI = runBlockingMaybeCancellable {
    val eelPath = path.asEelPath()
    if (eelPath.descriptor == LocalEelDescriptor) {
      // there is not mapping by Eel, hence the path may be considered local
      return@runBlockingMaybeCancellable path.toUri()
    }
    val root = eelPath.root.toString().replace('\\', '/')
    // see sun.nio.fs.WindowsUriSupport#toUri(java.lang.String, boolean, boolean)
    val trailing = if (eelPath.descriptor.operatingSystem == EelPath.OS.WINDOWS) "/" else ""
    URI("file", null, trailing + root + eelPath.parts.joinToString("/"), null, null)
  }

  /**
   * Transfers contents of a directory or file at [source] to [sink].
   * This function works efficiently when [source] and [sink] located on different environments.
   *
   * @param sink the required location for the file. If it is `null`, then a temporary directory will be created.
   * @return the path which contains the transferred data. Sometimes this value can coincide with [source] if the [sink]
   */
  @JvmStatic
  fun transferContentsIfNonLocal(eel: EelApi, source: Path, sink: Path? = null): Path {
    if (eel is LocalEelApi) return source
    if (source.getEelDescriptor() !is LocalEelDescriptor) {
      if (sink != null && source.getEelDescriptor() != sink.getEelDescriptor()) {
        throw UnsupportedOperationException("Transferring between different Eels is not supported yet")
      }
      return source
    }
    // todo: intergrate checksums here so that files could be refreshed in case of changes

    if (sink != null) {
      if (!Files.exists(sink)) {
        walkingTransfer(source, sink, false, true)
      }
      return sink
    }
    else {
      val temp = runBlockingMaybeCancellable { eel.createTempFor(source, false) }

      walkingTransfer(source, temp, false, true)

      return temp
    }
  }

  /**
   * Transfers a local file to a remote temporary environment if required.
   *
   * This function is useful for transferring files that are located on the local machine
   * to a remote environment. It can be particularly helpful for files stored in plugin
   * resources, such as:
   *
   * ```kotlin
   * Path.of(PathManager.getPluginsPath()).resolve(pluginId)
   * ```
   *
   * ### Behavior:
   * - If the file is **not local**, an exception will be thrown.
   * - If the `eel` is a local environment (`LocalEelApi`), the function directly returns the source as an [EelPath].
   * - If the file needs to be transferred to a remote environment:
   *   - A temporary directory is created on the remote environment.
   *   - The file is transferred into the temporary directory.
   *   - The temporary directory will be automatically deleted upon exit.
   *
   * ### Hash Calculation:
   * - **Purpose**: A SHA-256 hash is calculated for the source file to ensure that the file is transferred only when its contents have changed.
   * - **Mechanism**:
   *   - The hash is computed by reading the file in chunks (default: 1 MB) for memory efficiency.
   *   - A hash cache is maintained to store the relationship between the source file and its hash, reducing redundant file transfers.
   * - **Rehashing**:
   *   - If the file is modified (i.e., the current hash differs from the cached hash), the file is re-transferred to the remote environment, and the hash is updated.
   *
   * ### Parameters:
   * @param eel the [EelApi] instance representing the target environment (local or remote).
   * @param source the [Path] of the file to be transferred.
   *
   * ### Returns:
   * An [EelPath] representing the source file's location in the target environment.
   *
   * ### Exceptions:
   * - Throws [IllegalStateException] if the source file is not local.
   *
   * ### Example:
   * ```kotlin
   * val eel: EelApi = ...
   * val sourcePath = Path.of("/path/to/local/file.txt")
   *
   * val eelPath = transferLocalContentToRemoteTempIfNeeded(eel, sourcePath)
   * println("File transferred to: $eelPath")
   * ```
   *
   * ### Internal Details:
   * The function internally uses [TransferredContentHolder] to manage the caching and transfer logic:
   * - It checks the hash of the file using `MessageDigest` with the `SHA-256` algorithm.
   * - If the file's content is unchanged (based on the hash), the cached result is reused.
   * - If the content differs or is not in the cache, the file is transferred, and the hash is updated.
   *
   * ### See Also:
   * - [TransferredContentHolder]: For detailed caching and transfer mechanisms.
   * - [MessageDigest]: For hash calculation.
   */
  @JvmStatic
  @JvmOverloads
  fun transferLocalContentToRemoteTempIfNeeded(
    eel: EelApi,
    source: Path,
    fileAttributesStrategy: FileTransferAttributesStrategy = FileTransferAttributesStrategy.Copy,
  ): EelPath {
    val sourceDescriptor = source.getEelDescriptor()

    check(sourceDescriptor is LocalEelDescriptor)

    if (eel is LocalEelApi) {
      return source.asEelPath()
    }

    return runBlockingMaybeCancellable {
      service<TransferredContentHolder>().transferIfNeeded(eel, source, fileAttributesStrategy).asEelPath()
    }
  }

  @JvmStatic
  fun transferFileIfNonLocal(source: Path, remotePath: Path) {
    val sourceDescriptor = source.getEelDescriptor()

    check(sourceDescriptor is LocalEelDescriptor)

    val sourceHash = calculateFileHashUsingMetadata(source)

    val remoteHash = if (Files.exists(remotePath)) {
      calculateFileHashUsingMetadata(remotePath)
    }
    else {
      ""
    }
    if (sourceHash != remoteHash) {
      if (remoteHash.isNotEmpty()) {
        Files.delete(remotePath)
      }
      transferContentsIfNonLocal(remotePath.getEelDescriptor().upgradeBlocking(), source, remotePath)
    }
  }

  @Service
  private class TransferredContentHolder(private val scope: CoroutineScope) {
    // eel descriptor -> source path string ->> source hash -> transferred file
    private val cache = ConcurrentHashMap<Pair<EelDescriptor, String>, Deferred<Pair<String, Path>>>()


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun transferIfNeeded(eel: EelApi, source: Path, fileAttributesStrategy: FileTransferAttributesStrategy): Path {
      return cache.compute(eel.descriptor to source.toString()) { _, deferred ->
        if (deferred != null) {
          if (deferred.isCompleted) {
            val (oldSourceHash, _) = deferred.getCompleted()
            if (oldSourceHash == calculateFileHashUsingMetadata(source)) {
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
          calculateFileHashUsingMetadata(source) to temp
        }
      }!!.await().second
    }
  }

  private suspend fun EelApi.createTempFor(source: Path, deleteOnExit: Boolean): Path {
    return if (source.isDirectory()) {
      fs.createTemporaryDirectory(CreateTemporaryEntryOptions.Builder().deleteOnExit(deleteOnExit).build()).getOrThrowFileSystemException().asNioPath()
    }
    else {
      fs.createTemporaryFile(CreateTemporaryEntryOptions.Builder().deleteOnExit(deleteOnExit).build()).getOrThrowFileSystemException().asNioPath()
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
  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, fileAttributesStrategy: FileTransferAttributesStrategy) {
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

  suspend fun maybeUploadPath(scope: CoroutineScope, path: Path, target: EelDescriptor): EelPath {
    val originalPath = path.asEelPath()

    if (originalPath.descriptor == target) {
      return originalPath
    }

    val eelApi = target.upgrade()

    val options = CreateTemporaryEntryOptions.Builder()
      .prefix(path.fileName.toString())
      .suffix("eel")
      .deleteOnExit(true)
      .build()
    val tmpDir = eelApi.fs.createTemporaryDirectory(options).getOrThrow()
    val referencedPath = tmpDir.resolve(path.name)

    withContext(Dispatchers.IO) {
      walkingTransfer(path, referencedPath.asNioPath(), removeSource = false, copyAttributes = true)
    }

    scope.awaitCancellationAndInvoke {
      when (val result = eelApi.fs.delete(tmpDir, true)) {
        is EelResult.Ok -> Unit
        is EelResult.Error -> thisLogger().warn("Failed to delete temporary directory $tmpDir: ${result.error}")
      }
    }

    return referencedPath
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