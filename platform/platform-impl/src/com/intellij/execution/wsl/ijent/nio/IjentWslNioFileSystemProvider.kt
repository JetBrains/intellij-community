// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.ijent.*
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.platform.ijent.community.impl.nio.IjentPosixGroupPrincipal
import com.intellij.platform.ijent.community.impl.nio.IjentPosixUserPrincipal
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.attribute.PosixFilePermission.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ExecutorService
import kotlin.io.path.name

/**
 * A special wrapper for [com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider]
 * that makes it look like if it was a usual default file system provided by WSL.
 * For example, this wrapper adds Windows-like adapters to Posix permissions.
 *
 * Also, this wrapper delegates calls to the default file system [originalFsProvider]
 * for the methods not implemented in [ijentFsProvider] yet.
 */
internal class IjentWslNioFileSystemProvider(
  internal val ijentId: IjentId,
  internal val wslLocalRoot: Path,
  private val ijentFsProvider: FileSystemProvider,
  internal val originalFsProvider: FileSystemProvider,
) : DelegatingFileSystemProvider<IjentWslNioFileSystemProvider, IjentWslNioFileSystem>(), RoutingAwareFileSystemProvider {
  init {
    require(wslLocalRoot.isAbsolute)
  }

  private val ijentUserInfo: IjentInfo.User by lazy {
    val api = IjentSessionRegistry.instance().ijents[ijentId] ?: error("The session $ijentId was unregistered")
    api.info.user
  }

  override fun toString(): String = """${javaClass.simpleName}(ijentId=$ijentId, wslLocalRoot=$wslLocalRoot)"""

  override fun canHandleRouting(): Boolean = true

  private fun Path.toIjentPath(): IjentNioPath =
    if (this is IjentNioPath)
      this
    else
      fold(ijentFsProvider.getPath(ijentId.uri.resolve("/")) as IjentNioPath, IjentNioPath::resolve)

  private fun Path.toDefaultPath(): Path =
    if (this is IjentNioPath)
      wslLocalRoot.resolve(this)
    else
      this

  override fun getScheme(): String =
    originalFsProvider.scheme

  override fun wrapDelegateFileSystem(delegateFs: FileSystem): IjentWslNioFileSystem {
    val ijentFs =
      try {
        ijentFsProvider.getFileSystem(ijentId.uri)
      }
      catch (ignored: FileSystemNotFoundException) {
        ijentFsProvider.newFileSystem(ijentId.uri, null)
      }
    return IjentWslNioFileSystem(this, ijentFs = ijentFs, originalFs = delegateFs)
  }

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider =
    originalFsProvider

  // While the original file system implements more methods than the IJent FS, it make sens to keep it the default delegate and
  // convert paths in place for the IJent FS.
  // Everything may turn upside down later.
  override fun toDelegatePath(path: Path?): Path? =
    path?.toDefaultPath()

  override fun fromDelegatePath(path: Path?): Path? =
    path?.toDefaultPath()

  override fun newFileSystem(path: Path, env: MutableMap<String, *>?): IjentWslNioFileSystem {
    val ijentNioPath = path.toIjentPath()
    require(ijentNioPath.toUri() == ijentId.uri) { "${ijentNioPath.toUri()} != ${ijentId.uri}" }
    return IjentWslNioFileSystem(
      provider = this,
      ijentFs = ijentFsProvider.newFileSystem(ijentNioPath, env),
      originalFs = originalFsProvider.newFileSystem(Path.of("."), env),
    )
  }

  override fun getFileSystem(uri: URI): IjentWslNioFileSystem {
    require(uri == ijentId.uri) { "$uri != ${ijentId.uri}" }
    return IjentWslNioFileSystem(
      provider = this,
      ijentFs = ijentFsProvider.getFileSystem(uri),
      originalFsProvider.getFileSystem(URI("file:/"))
    )
  }

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): IjentWslNioFileSystem =
    getFileSystem(uri)

  override fun checkAccess(path: Path, vararg modes: AccessMode): Unit =
    ijentFsProvider.checkAccess(path.toIjentPath(), *modes)

  override fun newInputStream(path: Path, vararg options: OpenOption?): InputStream =
    ijentFsProvider.newInputStream(path.toIjentPath(), *options)

  override fun newOutputStream(path: Path, vararg options: OpenOption?): OutputStream =
    ijentFsProvider.newOutputStream(path.toIjentPath(), *options)

  override fun newFileChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): FileChannel =
    ijentFsProvider.newFileChannel(path.toIjentPath(), options, *attrs)

  override fun newAsynchronousFileChannel(
    path: Path?,
    options: MutableSet<out OpenOption>?,
    executor: ExecutorService?,
    vararg attrs: FileAttribute<*>?,
  ): AsynchronousFileChannel =
    originalFsProvider.newAsynchronousFileChannel(path, options, executor, *attrs)

  override fun createSymbolicLink(link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) {
    originalFsProvider.createSymbolicLink(link, target, *attrs)
  }

  override fun createLink(link: Path?, existing: Path?) {
    originalFsProvider.createLink(link, existing)
  }

  override fun deleteIfExists(path: Path?): Boolean =
    originalFsProvider.deleteIfExists(path)

  override fun readSymbolicLink(link: Path?): Path =
    originalFsProvider.readSymbolicLink(link)

  override fun getPath(uri: URI): Path =
    originalFsProvider.getPath(uri)

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    ijentFsProvider.newByteChannel(path.toIjentPath(), options, *attrs)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    object : DirectoryStream<Path> {
      val delegate = ijentFsProvider.newDirectoryStream(dir.toIjentPath(), filter)

      override fun iterator(): MutableIterator<Path> =
        object : MutableIterator<Path> {
          val delegateIterator = delegate.iterator()

          override fun hasNext(): Boolean =
            delegateIterator.hasNext()

          override fun next(): Path {
            // resolve() can't be used there because WindowsPath.resolve() checks that the other path is WindowsPath.
            val ijentPath = delegateIterator.next()
            return ijentPath.asSequence().map(Path::name).map(::sanitizeFileName).fold(wslLocalRoot, Path::resolve)
          }

          override fun remove() {
            delegateIterator.remove()
          }
        }

      override fun close() {
        delegate.close();
      }
    }

  override fun createDirectory(dir: Path?, vararg attrs: FileAttribute<*>?) {
    originalFsProvider.createDirectory(dir, *attrs)
  }

  override fun delete(path: Path?) {
    originalFsProvider.delete(path)
  }

  override fun copy(source: Path?, target: Path?, vararg options: CopyOption?) {
    originalFsProvider.copy(source, target, *options)
  }

  override fun move(source: Path?, target: Path?, vararg options: CopyOption?) {
    originalFsProvider.move(source, target, *options)
  }

  override fun isSameFile(path: Path?, path2: Path?): Boolean =
    originalFsProvider.isSameFile(path, path2)

  override fun isHidden(path: Path?): Boolean =
    originalFsProvider.isHidden(path)

  override fun getFileStore(path: Path?): FileStore =
    originalFsProvider.getFileStore(path)

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path?, type: Class<V>, vararg options: LinkOption): V =
    originalFsProvider.getFileAttributeView(path, type, *options)

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    // There's some contract violation at least in com.intellij.openapi.util.io.FileAttributes.fromNio:
    // the function always assumes that the returned object is DosFileAttributes on Windows,
    // and that's always true with the default WindowsFileSystemProvider.

    val actualType = when (ijentUserInfo) {
      is IjentPosixInfo.User ->
        if (DosFileAttributes::class.java.isAssignableFrom(type)) PosixFileAttributes::class.java
        else type

      is IjentWindowsInfo.User -> TODO()
    }

    val actualAttrs = ijentFsProvider.readAttributes(path.toIjentPath(), actualType, *options)

    val resultAttrs = when (actualAttrs) {
      is DosFileAttributes -> actualAttrs

      is PosixFileAttributes ->
        when (val ijentUserInfo = ijentUserInfo) {
          is IjentPosixInfo.User ->
            IjentNioPosixFileAttributesWithDosAdapter(ijentUserInfo, actualAttrs, path.name.startsWith("."))

          is IjentWindowsInfo.User ->
            actualAttrs
        }

      else -> actualAttrs
    }

    return type.cast(resultAttrs)
  }

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    ijentFsProvider.readAttributes(path.toIjentPath(), attributes, *options)

  override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
    originalFsProvider.setAttribute(path, attribute, value, *options)
  }
}

@VisibleForTesting
@ApiStatus.Internal
class IjentNioPosixFileAttributesWithDosAdapter(
  private val userInfo: IjentPosixInfo.User,
  private val fileInfo: PosixFileAttributes,
  private val nameStartsWithDot: Boolean,
) : PosixFileAttributes by fileInfo, DosFileAttributes {
  /**
   * Returns `false` if the corresponding file or directory can be modified.
   * Note that returning `true` does not mean that the corresponding file can be read or the directory can be listed.
   */
  override fun isReadOnly(): Boolean = fileInfo.run {
    val owner = owner()
    val group = group()
    return when {
      owner is IjentPosixUserPrincipal && owner.uid == userInfo.uid ->
        OWNER_WRITE !in permissions() || (isDirectory && OWNER_EXECUTE !in permissions())

      group is IjentPosixGroupPrincipal && group.gid == userInfo.gid ->
        GROUP_WRITE !in permissions() || (isDirectory && GROUP_EXECUTE !in permissions())

      else ->
        OTHERS_WRITE !in permissions() || (isDirectory && OTHERS_EXECUTE !in permissions())
    }
  }

  override fun isHidden(): Boolean = nameStartsWithDot

  override fun isArchive(): Boolean = false

  override fun isSystem(): Boolean = false
}