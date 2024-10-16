// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.CaseSensitivityAttribute
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2.FetchAttributesFilter
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.ijent.community.impl.nio.EelPosixGroupPrincipal
import com.intellij.platform.ijent.community.impl.nio.EelPosixUserPrincipal
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.platform.ijent.community.impl.nio.IjentNioPosixFileAttributes
import com.intellij.util.io.createDirectories
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.VisibleForTesting
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import java.nio.file.attribute.PosixFilePermission.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.readAttributes
import kotlin.io.path.relativeTo

/**
 * A special wrapper for [com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider]
 * that makes it look like if it was a usual default file system provided by WSL.
 * For example, this wrapper adds Windows-like adapters to Posix permissions.
 *
 * Also, this wrapper delegates calls to the default file system [originalFsProvider]
 * for the methods not implemented in [ijentFsProvider] yet.
 *
 * Normally, there's no need to create this filesystem manually because [com.intellij.execution.wsl.ijent.nio.toggle.IjentWslNioFsToggler]
 * does this job automatically.
 * It should be used even for manual creation of filesystems.
 * Nevertheless, in case when this filesystem should be accessed directly,
 * an instance of [IjentWslNioFileSystem] can be obtained with a URL like "ijent://wsl/distribution-name".
 */
class IjentWslNioFileSystemProvider(
  wslDistribution: WSLDistribution,
  private val ijentFsProvider: FileSystemProvider,
  internal val originalFsProvider: FileSystemProvider,
) : FileSystemProvider(), RoutingAwareFileSystemProvider {
  private val ijentFsUri: URI = URI("ijent", "wsl", "/${wslDistribution.id}", null, null)
  private val wslLocalRoot: Path = originalFsProvider.getFileSystem(URI("file:/")).getPath(wslDistribution.getWindowsPath("/"))
  private val createdFileSystems: MutableMap<String, IjentWslNioFileSystem> = ConcurrentHashMap()

  internal fun removeFileSystem(wslId: String) {
    createdFileSystems.remove(wslId)
  }

  override fun toString(): String = """${javaClass.simpleName}(${wslLocalRoot})"""

  override fun canHandleRouting(): Boolean = true

  internal fun toIjentNioPath(path: Path): IjentNioPath = path.toIjentPath()

  private fun Path.toIjentPath(): IjentNioPath =
    when (this) {
      is IjentNioPath -> this
      is IjentWslNioPath -> delegate.toIjentPath()
      else -> fold(ijentFsProvider.getPath(ijentFsUri) as IjentNioPath, IjentNioPath::resolve)
    }

  internal fun toOriginalPath(path: Path): Path = path.toOriginalPath()

  private fun Path.toOriginalPath(): Path =
    when (this) {
      is IjentNioPath -> fold(wslLocalRoot) { parent, file -> parent.resolve(file.toString()) }
      is IjentWslNioPath -> delegate.toOriginalPath()
      else -> this
    }

  override fun getScheme(): String =
    originalFsProvider.scheme

  override fun newFileSystem(path: Path, env: MutableMap<String, *>?): IjentWslNioFileSystem =
    getFileSystem(path.toUri())

  override fun getFileSystem(uri: URI): IjentWslNioFileSystem {
    require(uri.scheme == scheme) { "Wrong scheme in `$uri` (expected `$scheme`)" }
    val wslId = wslIdFromPath(originalFsProvider.getPath(uri))
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

  private fun wslIdFromPath(path: Path): String {
    val root = path.toAbsolutePath().root.toString()
    require(root.startsWith("""\\wsl""")) { "`$path` doesn't look like a file on WSL" }
    val wslId = root.removePrefix("""\\wsl""").substringAfter('\\').trimEnd('\\')
    return wslId
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
    originalFsProvider.newAsynchronousFileChannel(path.toOriginalPath(), options, executor, *attrs)

  override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>?) {
    ijentFsProvider.createSymbolicLink(link.toIjentPath(), target.toIjentPath(), *attrs)
  }

  override fun createLink(link: Path, existing: Path) {
    originalFsProvider.createLink(link.toOriginalPath(), existing.toOriginalPath())
  }

  override fun deleteIfExists(path: Path): Boolean =
    ijentFsProvider.deleteIfExists(path.toIjentPath())

  override fun readSymbolicLink(link: Path): IjentWslNioPath =
    IjentWslNioPath(
      getFileSystem(wslIdFromPath(link)),
      ijentFsProvider.readSymbolicLink(link.toIjentPath()),
      null,
    )

  override fun getPath(uri: URI): Path =
    IjentWslNioPath(
      getFileSystem(wslIdFromPath(originalFsProvider.getPath(uri))),
      originalFsProvider.getPath(uri),
      null,
    )

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    ijentFsProvider.newByteChannel(path.toIjentPath(), options, *attrs)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    object : DirectoryStream<Path> {
      val delegate = ijentFsProvider.newDirectoryStream(dir.toIjentPath(), filter)
      val wslId = wslIdFromPath(dir)

      override fun iterator(): MutableIterator<Path> =
        object : MutableIterator<Path> {
          val delegateIterator = delegate.iterator()

          override fun hasNext(): Boolean =
            delegateIterator.hasNext()

          override fun next(): Path {
            // resolve() can't be used there because WindowsPath.resolve() checks that the other path is WindowsPath.
            val ijentPath = delegateIterator.next().toIjentPath()

            val originalPath = ijentPath.asSequence().map(Path::name).map(::sanitizeFileName).fold(wslLocalRoot, Path::resolve)

            val cachedAttrs = ijentPath.get() as IjentNioPosixFileAttributes?
            val dosAttributes =
              if (cachedAttrs != null)
                IjentNioPosixFileAttributesWithDosAdapter(
                  ijentPath.fileSystem.ijentFs.user as EelUserPosixInfo,
                  cachedAttrs,
                  nameStartsWithDot = ijentPath.eelPath.fileName.startsWith("."),
                )
              else null

            return IjentWslNioPath(getFileSystem(wslId), originalPath, dosAttributes)
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
  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    val sourceWsl = WslPath.parseWindowsUncPath(source.root.toString())
    val targetWsl = WslPath.parseWindowsUncPath(target.root.toString())
    when {
      sourceWsl != null && sourceWsl == targetWsl -> {
        ijentFsProvider.copy(source.toIjentPath(), target.toIjentPath(), *options)
      }

      sourceWsl == null && targetWsl == null -> {
        LOG.warn("This branch is not supposed to execute. Copying ${source} => ${target} through inappropriate FileSystemProvider")
        originalFsProvider.copy(source.toOriginalPath(), target.toOriginalPath(), *options)
      }

      else -> {
        walkingTransfer(source, target, removeSource = false)
      }
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    val sourceWsl = WslPath.parseWindowsUncPath(source.root.toString())
    val targetWsl = WslPath.parseWindowsUncPath(target.root.toString())
    when {
      sourceWsl != null && sourceWsl == targetWsl -> {
        ijentFsProvider.move(source.toIjentPath(), target.toIjentPath(), *options)
      }

      sourceWsl == null && targetWsl == null -> {
        LOG.warn("This branch is not supposed to execute. Moving ${source} => ${target} through inappropriate FileSystemProvider")
        originalFsProvider.move(source.toOriginalPath(), target.toOriginalPath(), *options)
      }

      else -> {
        walkingTransfer(source, target, removeSource = true)
      }
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean =
    originalFsProvider.isSameFile(path.toOriginalPath(), path2.toOriginalPath())

  override fun isHidden(path: Path): Boolean =
    originalFsProvider.isHidden(path.toOriginalPath())

  override fun getFileStore(path: Path): FileStore =
    ijentFsProvider.getFileStore(path.toIjentPath())

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V =
    originalFsProvider.getFileAttributeView(path.toOriginalPath(), type, *options)

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    // There's some contract violation at least in com.intellij.openapi.util.io.FileAttributes.fromNio:
    // the function always assumes that the returned object is DosFileAttributes on Windows,
    // and that's always true with the default WindowsFileSystemProvider.

    val actualType =
      if (DosFileAttributes::class.java.isAssignableFrom(type)) PosixFileAttributes::class.java
      else type

    val ijentNioPath = path.toIjentPath()
    val resultAttrs = when (val actualAttrs = ijentFsProvider.readAttributes(ijentNioPath, actualType, *options)) {
      is DosFileAttributes -> actualAttrs  // TODO How can it be possible? It's certainly known that the remote OS is GNU/Linux.

      is PosixFileAttributes ->
        IjentNioPosixFileAttributesWithDosAdapter(
          ijentNioPath.fileSystem.ijentFs.user as EelUserPosixInfo,
          actualAttrs, path.name.startsWith("."),
        )

      else -> actualAttrs
    }

    return type.cast(resultAttrs)
  }

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    ijentFsProvider.readAttributes(path.toIjentPath(), attributes, *options)

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    originalFsProvider.setAttribute(path.toOriginalPath(), attribute, value, *options)
  }

  companion object {
    private val LOG = logger<IjentWslNioFileSystemProvider>()

    @VisibleForTesting
    fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean) {
      val sourceStack = ArrayDeque<Path>()
      sourceStack.add(sourceRoot)

      var lastDirectory: Path? = null

      while (true) {
        val source =
          try {
            sourceStack.removeLast()
          }
          catch (_: NoSuchElementException) {
            break
          }

        while (removeSource && lastDirectory != null && lastDirectory != sourceRoot && source.parent != lastDirectory) {
          Files.delete(lastDirectory)
          lastDirectory = lastDirectory.parent
        }

        val stat =
          BasicFileAttributesHolder2.getAttributesFromHolder(source)
          ?: source.readAttributes(LinkOption.NOFOLLOW_LINKS)

        // WindowsPath doesn't support resolve() from paths of different class.
        val target = source.relativeTo(sourceRoot).fold(targetRoot) { parent, file ->
          parent.resolve(file.toString())
        }

        when {
          stat.isDirectory -> {
            lastDirectory = source
            try {
              target.createDirectories()
            }
            catch (err: FileAlreadyExistsException) {
              if (!Files.isDirectory(target)) {
                throw err
              }
            }
            source.fileSystem.provider().newDirectoryStream(source, FetchAttributesFilter.ACCEPT_ALL).use { children ->
              sourceStack.addAll(children.toList().asReversed())
            }
          }

          stat.isRegularFile -> {
            Files.newInputStream(source, READ).use { reader ->
              Files.newOutputStream(target, CREATE, TRUNCATE_EXISTING, WRITE).use { writer ->
                reader.copyTo(writer)
              }
            }
            if (removeSource) {
              Files.delete(source)
            }
          }

          else -> {
            LOG.info("Not copying $source to $target because the source file is neither a regular file nor a directory")
          }
        }
      }

      while (removeSource && lastDirectory != null && lastDirectory != sourceRoot) {
        Files.delete(lastDirectory)
        lastDirectory = lastDirectory.parent
      }
    }
  }
}

@VisibleForTesting
class IjentNioPosixFileAttributesWithDosAdapter(
  private val userInfo: EelUserPosixInfo,
  private val fileInfo: PosixFileAttributes,
  private val nameStartsWithDot: Boolean,
) : CaseSensitivityAttribute, PosixFileAttributes by fileInfo, DosFileAttributes {
  /**
   * Returns `false` if the corresponding file or directory can be modified.
   * Note that returning `true` does not mean that the corresponding file can be read or the directory can be listed.
   */
  override fun isReadOnly(): Boolean = fileInfo.run {
    val owner = owner()
    val group = group()
    return when {
      owner is EelPosixUserPrincipal && owner.uid == userInfo.uid ->
        OWNER_WRITE !in permissions() || (isDirectory && OWNER_EXECUTE !in permissions())

      group is EelPosixGroupPrincipal && group.gid == userInfo.gid ->
        GROUP_WRITE !in permissions() || (isDirectory && GROUP_EXECUTE !in permissions())

      else ->
        OTHERS_WRITE !in permissions() || (isDirectory && OTHERS_EXECUTE !in permissions())
    }
  }

  override fun isHidden(): Boolean = nameStartsWithDot

  override fun isArchive(): Boolean = false

  override fun isSystem(): Boolean = false

  override fun getCaseSensitivity(): FileAttributes.CaseSensitivity {
    if (fileInfo is CaseSensitivityAttribute) return fileInfo.caseSensitivity else return FileAttributes.CaseSensitivity.UNKNOWN
  }
}