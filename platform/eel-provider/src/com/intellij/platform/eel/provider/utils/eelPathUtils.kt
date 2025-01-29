// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
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
import java.nio.file.StandardOpenOption
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
      val file = eel.fs.createTemporaryFile(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).build()).getOrThrowFileSystemException()
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
    val file = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().prefix(prefix).build()).getOrThrowFileSystemException()
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
  fun transferContentsIfNonLocal(eel: EelApi, source: Path, sink: Path?): Path {
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
  fun transferLocalContentToRemoteTempIfNeeded(eel: EelApi, source: Path): EelPath {
    val sourceDescriptor = source.getEelDescriptor()

    check(sourceDescriptor is LocalEelDescriptor)

    if (eel is LocalEelApi) {
      return source.asEelPath()
    }

    return runBlockingMaybeCancellable {
      service<TransferredContentHolder>().transferIfNeeded(eel, source).asEelPath()
    }
  }

  @JvmStatic
  fun transferLocalContentToRemotePathIfNeeded(source: Path, remotePath: Path) {
    val sourceDescriptor = source.getEelDescriptor()

    check(sourceDescriptor is LocalEelDescriptor)

    val sourceHash = calculateFileHash(source)

    val remoteHash = if (Files.exists(remotePath)) {
      calculateFileHash(remotePath)
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
    suspend fun transferIfNeeded(eel: EelApi, source: Path): Path {
      return cache.compute(eel.descriptor to source.toString()) { _, deferred ->
        if (deferred != null) {
          if (deferred.isCompleted) {
            val (oldSourceHash, _) = deferred.getCompleted()
            if (oldSourceHash == calculateFileHash(source)) {
              return@compute deferred
            }
          }
          else {
            return@compute deferred
          }
        }

        scope.async {
          val temp = eel.createTempFor(source, true)
          walkingTransfer(source, temp, false, true)
          calculateFileHash(source) to temp
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

  private fun calculateFileHash(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
    val fileSize = attributes.size()
    val lastModified = attributes.lastModifiedTime().toMillis()

    digest.update(fileSize.toString().toByteArray())
    digest.update(lastModified.toString().toByteArray())

    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
      val buffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024)
      while (channel.read(buffer) > 0) {
        buffer.flip()
        digest.update(buffer)
        buffer.clear()
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

  @RequiresBackgroundThread
  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, copyAttributes: Boolean) {
    val sourceStack = ArrayDeque<Path>()
    sourceStack.add(sourceRoot)

    class LastDirectoryInfo(
      val parent: LastDirectoryInfo?,
      val source: Path,
      val target: Path,
      val sourceAttrs: BasicFileAttributes,
    )

    var lastDirectory: LastDirectoryInfo? = null

    while (true) {
      val source = try {
        sourceStack.removeLast()
      }
      catch (_: NoSuchElementException) {
        break
      }

      if (removeSource || copyAttributes) {
        while (lastDirectory != null && lastDirectory.source != sourceRoot && source.parent != lastDirectory.source) {
          if (removeSource) {
            Files.delete(lastDirectory.source)
          }
          if (copyAttributes) {
            copyAttributes(lastDirectory.source, lastDirectory.target, lastDirectory.sourceAttrs)
          }
          lastDirectory = lastDirectory.parent
        }
      }

      // WindowsPath doesn't support resolve() from paths of different class.
      val target = source.relativeTo(sourceRoot).fold(targetRoot) { parent, file ->
        parent.resolve(file.toString())
      }

      val sourceAttrs: BasicFileAttributes = readSourceAttrs(source, target, withExtendedAttributes = copyAttributes)

      when {
        sourceAttrs.isDirectory -> {
          lastDirectory = LastDirectoryInfo(lastDirectory, source, target, sourceAttrs)
          try {
            target.createDirectories()
          }
          catch (err: FileAlreadyExistsException) {
            if (!Files.isDirectory(target)) {
              throw err
            }
          }
          source.fileSystem.provider().newDirectoryStream(source, { true }).use { children ->
            sourceStack.addAll(children.toList().asReversed())
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
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
          }
        }

        sourceAttrs.isSymbolicLink -> {
          Files.copy(source, target, LinkOption.NOFOLLOW_LINKS)
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
          }
          if (removeSource) {
            Files.delete(source)
          }
          if (copyAttributes) {
            copyAttributes(source, target, sourceAttrs)
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

    if (removeSource || copyAttributes) {
      while (lastDirectory != null && lastDirectory.source != sourceRoot) {
        if (removeSource) {
          Files.delete(lastDirectory.source)
        }
        if (copyAttributes) {
          copyAttributes(lastDirectory.source, lastDirectory.target, lastDirectory.sourceAttrs)
        }
        lastDirectory = lastDirectory.parent
      }
    }
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

  private fun copyAttributes(source: Path, target: Path, sourceAttrs: BasicFileAttributes) {
    if (sourceAttrs is PosixFileAttributes) {
      try {
        val targetView = Files.getFileAttributeView(target, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        if (targetView != null) {
          // TODO It's ineffective for IjentNioFS, because there are 6 consequential system calls.
          targetView.setPermissions(sourceAttrs.permissions())
          runCatching<UnsupportedOperationException>(
            { targetView.setOwner(sourceAttrs.owner()) },
            { targetView.setGroup(sourceAttrs.group()) }
          )
        }
      }
      catch (err: IOException) {
        LOG.info("Failed to copy Posix file attributes from $source to $target: $err")
      }
    }

    if (sourceAttrs is DosFileAttributes) {
      try {
        val targetView = Files.getFileAttributeView(target, DosFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        if (targetView != null) {
          targetView.setHidden(sourceAttrs.isHidden)
          targetView.setSystem(sourceAttrs.isSystem)
          targetView.setArchive(sourceAttrs.isArchive)
          targetView.setReadOnly(sourceAttrs.isReadOnly)
        }
      }
      catch (err: IOException) {
        LOG.info("Failed to copy Windows file attributes from $source to $target: $err")
      }
    }

    try {
      Files.getFileAttributeView(target, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).setTimes(
        sourceAttrs.lastModifiedTime(),
        sourceAttrs.lastAccessTime(),
        sourceAttrs.creationTime(),
      )
    }
    catch (err: IOException) {
      LOG.info("Failed to copy basic file attributes from $source to $target: $err")
    }
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