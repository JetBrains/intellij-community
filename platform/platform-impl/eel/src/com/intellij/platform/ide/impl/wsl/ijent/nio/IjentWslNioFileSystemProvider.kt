// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio

import com.intellij.execution.ijent.nio.getCachedFileAttributesAndWrapToDosAttributesAdapter
import com.intellij.execution.ijent.nio.readAttributesUsingDosAttributesAdapter
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi

/**
 * A special wrapper for [com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider]
 * that makes it look like if it was a usual default file system provided by WSL.
 * For example, this wrapper adds Windows-like adapters to Posix permissions.
 *
 * Also, this wrapper delegates calls to the default file system [originalFsProvider]
 * for the methods not implemented in [ijentFsProvider] yet.
 *
 * Normally, there's no need to create this filesystem manually because [com.intellij.platform.ide.impl.wsl.ijent.nio.toggle.IjentWslNioFsToggler]
 * does this job automatically.
 * It should be used even for manual creation of filesystems.
 * Nevertheless, in case when this filesystem should be accessed directly,
 * an instance of [IjentWslNioFileSystem] can be obtained with a URL like "ijent://wsl/distribution-name".
 */
@ApiStatus.Internal
class IjentWslNioFileSystemProvider(
  val wslId: @NlsSafe String,
  private val ijentFsProvider: FileSystemProvider,
  internal val originalFsProvider: FileSystemProvider,
) : FileSystemProvider(), RoutingAwareFileSystemProvider {
  private val ijentFsUri: URI = URI("ijent", "wsl", "/$wslId", null, null)
  private val originalFs = originalFsProvider.getFileSystem(URI("file:/"))
  private val createdFileSystems: MutableMap<String, IjentWslNioFileSystem> = ConcurrentHashMap()

  internal fun removeFileSystem(wslId: String) {
    createdFileSystems.remove(wslId)
  }

  override fun toString(): String = """${javaClass.simpleName}(${wslId})"""

  override fun canHandleRouting(path: Path): Boolean = true

  internal fun toIjentNioPath(path: Path): IjentNioPath = path.toIjentPath()

  private fun Path.toIjentPath(): IjentNioPath =
    when {
      this is IjentNioPath -> this
      this is IjentWslNioPath -> presentablePath.toIjentPath()

      isAbsolute ->
        fold(ijentFsProvider.getPath(ijentFsUri) as IjentNioPath, { nioPath, newPart -> nioPath.resolve(newPart.toString()) })

      else -> {
        val ijentNioFs = ijentFsProvider.getFileSystem(ijentFsUri)
        ijentNioFs.getPath(toString().replace("\\", ijentNioFs.separator)) as IjentNioPath
      }
    }

  internal fun toOriginalPath(path: Path, notation: String): Path = path.toOriginalPath(notation)

  private tailrec fun Path.toOriginalPath(notation: String): Path {
    val notationLowerCase = notation.lowercase()
    assert(notationLowerCase == "wsl.localhost" || notationLowerCase == "wsl$") { notation }
    return when (this) {
      is IjentNioPath -> fold(originalFs.getPath("\\\\$notation\\$wslId\\")) { parent, file -> parent.resolve(file.toString()) }
      is IjentWslNioPath -> presentablePath.toOriginalPath(notation)
      else -> this
    }
  }

  private tailrec fun Path.toOriginalPathWithSameNotation(): Path {
    return when (this) {
      is IjentNioPath -> error(this)
      is IjentWslNioPath -> presentablePath.toOriginalPathWithSameNotation()
      else -> this
    }
  }

  override fun getScheme(): String =
    originalFsProvider.scheme

  override fun newFileSystem(path: Path, env: MutableMap<String, *>?): IjentWslNioFileSystem =
    getFileSystem(path.toUri())

  override fun getFileSystem(uri: URI): IjentWslNioFileSystem {
    require(uri.scheme == scheme) { "Wrong scheme in `$uri` (expected `$scheme`)" }
    val wslId = wslIdFromPath(originalFsProvider.getPath(uri))
                ?: throw FileSystemNotFoundException(
                  "Cannot find WSL distribution for $uri. The URL is incorrect or the distribution does not exist.")
    return getFileSystem(wslId)
  }

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): IjentWslNioFileSystem =
    getFileSystem(uri)

  private fun getFileSystem(wslId: String): IjentWslNioFileSystem {
    return createdFileSystems.computeIfAbsent(wslId) { wslId ->
      IjentWslNioFileSystem(
        provider = this,
        wslId = wslId,
        ijentFs = ijentFsProvider.getFileSystem(URI("ijent", "wsl", "/$wslId", null, null)),
        originalFsProvider.getFileSystem(URI("file:/"))
      )
    }
  }

  private fun wslIdFromPath(path: Path): String? {
    val root = path.toAbsolutePath().root.toString()
    val wslMarker = """\\wsl"""
    if (!root.startsWith(wslMarker, ignoreCase = true)) return null
    val wslIdWithProbablyWrongCase = root.substring(wslMarker.length).substringAfter('\\').trimEnd('\\')
    return allWslDistributionIds.get().singleOrNull { wslId -> wslId.equals(wslIdWithProbablyWrongCase, ignoreCase = true) }
  }

  override fun checkAccess(path: Path, vararg modes: AccessMode): Unit =
    ijentFsProvider.checkAccess(path.toIjentPath(), *modes)

  override fun newInputStream(path: Path, vararg options: OpenOption?): InputStream =
    ijentFsProvider.newInputStream(path.toIjentPath(), *options)

  override fun newOutputStream(path: Path, vararg options: OpenOption?): OutputStream =
    ijentFsProvider.newOutputStream(path.toIjentPath(), *options)

  override fun newFileChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): FileChannel =
    ijentFsProvider.newFileChannel(path.toIjentPath(), options, *attrs)

  override fun newAsynchronousFileChannel(
    path: Path,
    options: MutableSet<out OpenOption>?,
    executor: ExecutorService?,
    vararg attrs: FileAttribute<*>?,
  ): AsynchronousFileChannel =
    // TODO Implement me.
    originalFsProvider.newAsynchronousFileChannel(path.toOriginalPathWithSameNotation(), options, executor, *attrs)

  override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>?) {
    ijentFsProvider.createSymbolicLink(link.toIjentPath(), target.toIjentPath(), *attrs)
  }

  override fun createLink(link: Path, existing: Path) {
    // TODO It will fail anyway. Maybe throw an error right there?
    originalFsProvider.createLink(link.toOriginalPathWithSameNotation(), existing.toOriginalPathWithSameNotation())
  }

  override fun deleteIfExists(path: Path): Boolean =
    ijentFsProvider.deleteIfExists(path.toIjentPath())

  override fun readSymbolicLink(link: Path): IjentWslNioPath =
    IjentWslNioPath(
      getFileSystem(
        wslIdFromPath(link)
        ?: throw IOException("Cannot find WSL distribution for $link. The URL is incorrect or the distribution does not exist.")
      ),
      ijentFsProvider.readSymbolicLink(link.toIjentPath()),
      null,
    )

  override fun getPath(uri: URI): Path =
    IjentWslNioPath(
      getFileSystem(
        wslIdFromPath(originalFsProvider.getPath(uri))
        ?: throw IllegalArgumentException("Cannot find WSL distribution for $uri. The URL is incorrect or the distribution does not exist.")
      ),
      originalFsProvider.getPath(uri),
      null,
    )

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    ijentFsProvider.newByteChannel(path.toIjentPath(), options, *attrs)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    object : DirectoryStream<Path> {
      val delegate = ijentFsProvider.newDirectoryStream(dir.toIjentPath(), filter)
      val wslId =
        wslIdFromPath(dir)
        ?: throw FileSystemException("Cannot find WSL distribution for $dir. The URL is incorrect or the distribution does not exist.")

      override fun iterator(): MutableIterator<Path> =
        object : MutableIterator<Path> {
          val delegateIterator = delegate.iterator()

          override fun hasNext(): Boolean =
            delegateIterator.hasNext()

          override fun next(): Path {
            // resolve() can't be used there because WindowsPath.resolve() checks that the other path is WindowsPath.
            val ijentPath = delegateIterator.next().toIjentPath()
            val originalPath = dir.resolve(sanitizeFileName(ijentPath.fileName.toString()))

            return IjentWslNioPath(getFileSystem(wslId), originalPath.toOriginalPathWithSameNotation(), ijentPath.getCachedFileAttributesAndWrapToDosAttributesAdapter())
          }

          override fun remove() {
            delegateIterator.remove()
          }
        }

      override fun close() {
        delegate.close()
      }
    }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    ijentFsProvider.createDirectory(dir.toIjentPath(), *attrs)
  }

  override fun delete(path: Path) {
    ijentFsProvider.delete(path.toIjentPath())
  }

  @OptIn(ExperimentalPathApi::class)
  override fun copy(source: Path, target: Path, vararg options: CopyOption) {
    val sourceWsl = WslPath.parseWindowsUncPath(source.root.toString())
    val targetWsl = WslPath.parseWindowsUncPath(target.root.toString())
    when {
      sourceWsl != null && sourceWsl == targetWsl -> {
        ijentFsProvider.copy(source.toIjentPath(), target.toIjentPath(), *options)
      }

      sourceWsl == null && targetWsl == null -> {
        LOG.warn("This branch is not supposed to execute. Copying ${source} => ${target} through inappropriate FileSystemProvider")
        originalFsProvider.copy(source.toOriginalPathWithSameNotation(), target.toOriginalPathWithSameNotation(), *options)
      }

      else -> {
        EelPathUtils.walkingTransfer(source, target, removeSource = false, copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
      }
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption) {
    val sourceWsl = WslPath.parseWindowsUncPath(source.root.toString())
    val targetWsl = WslPath.parseWindowsUncPath(target.root.toString())
    when {
      sourceWsl != null && sourceWsl == targetWsl -> {
        ijentFsProvider.move(source.toIjentPath(), target.toIjentPath(), *options)
      }

      sourceWsl == null && targetWsl == null -> {
        LOG.warn("This branch is not supposed to execute. Moving ${source} => ${target} through inappropriate FileSystemProvider")
        originalFsProvider.move(source.toOriginalPathWithSameNotation(), target.toOriginalPathWithSameNotation(), *options)
      }

      else -> {
        EelPathUtils.walkingTransfer(source, target, removeSource = true, copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
      }
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    if (path !is IjentWslNioPath) {
      if (path2 !is IjentWslNioPath) {
        throw ProviderMismatchException(
          "Neither $path (${path::class}) nor $path2 (${path2::class}) are ${IjentWslNioPath::class.java.name}"
        )
      }
      return isSameFile(path2, path)
    }

    if (path2 !is IjentWslNioPath) {
      return if (path.actualPath.fileSystem.provider() == path2.fileSystem.provider())
        Files.isSameFile(path.actualPath, path2)
      else
        false
    }

    if (path.actualPath == path.presentablePath && path2.actualPath == path2.presentablePath) {
      return Files.isSameFile(path.toIjentPath(), path2.toIjentPath())
    }

    if (path.actualPath.fileSystem.provider() == path2.actualPath.fileSystem.provider()) {
      return Files.isSameFile(path.actualPath, path2.actualPath)
    }

    return false
  }

  override fun isHidden(path: Path): Boolean =
    originalFsProvider.isHidden(path.toOriginalPathWithSameNotation())

  override fun getFileStore(path: Path): FileStore =
    ijentFsProvider.getFileStore(path.toIjentPath())

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V =
    ijentFsProvider.getFileAttributeView(path.toIjentPath(), type, *options)

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    return ijentFsProvider.readAttributesUsingDosAttributesAdapter(path, path.toIjentPath(), type, *options)
  }

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    ijentFsProvider.readAttributes(path.toIjentPath(), attributes, *options)

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    ijentFsProvider.setAttribute(path.toIjentPath(), attribute, value, *options)
  }

  companion object {
    private val allWslDistributionIds: AtomicReference<Set<String>> by lazy {
      val ref = AtomicReference(emptySet<String>())
      val wslDistributionManager = WslDistributionManager.getInstance()
      wslDistributionManager.addWslDistributionsChangeListener { old, new ->
        ref.updateAndGet { oldFromRef ->
          val result = HashSet(oldFromRef)
          result.removeAll(old.map { it.id })
          result.addAll(new.map { it.id })
          result
        }
      }
      ref.set(wslDistributionManager.installedDistributions.map { it.id }.toHashSet())
      ref
    }

    private val LOG = logger<IjentWslNioFileSystemProvider>()
  }
}