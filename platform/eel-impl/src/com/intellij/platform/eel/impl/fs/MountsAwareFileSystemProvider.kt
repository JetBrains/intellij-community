// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelMountProvider
import com.intellij.platform.eel.provider.EelMountRoot
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.transformPath
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ExecutorService

/**
 * A file system provider that optimizes access to files in Docker containers by directly accessing
 * mounted volumes when possible, instead of going through ijent.
 */
abstract class MountsAwareFileSystemProvider(
  protected val delegate: FileSystemProvider,
  private val mountProvider: EelMountProvider
) : DelegatingFileSystemProvider<MountsAwareFileSystemProvider, MountsAwareFileSystem>() {

  override fun wrapDelegateFileSystem(delegateFs: FileSystem): MountsAwareFileSystem = MountsAwareFileSystem(this, delegateFs)
  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider = delegate
  override fun wrapDelegatePath(delegatePath: Path?): Path? = delegatePath
  override fun toDelegatePath(path: Path?): Path? = path

  abstract fun unwrapEelPath(path: Path): Pair<EelPath, EelFileSystemApi>?

  /**
   * Checks if the given path is in a mounted volume and can be accessed directly.
   * Returns the local path if it can be accessed directly, null otherwise.
   */
  private fun getDirectAccessPath(path: Path?, directAccess: EelMountRoot.DirectAccessOptions = EelMountRoot.DirectAccessOptions.BasicAttributes): Path? {
    if (path == null) return null
    val (eelPath, eelFsApi) = unwrapEelPath(path) ?: return null
    val mountRoot = mountProvider.getMountRoot(eelPath) ?: return null
    val directAccessPath = mountRoot.transformPath(eelPath).asNioPath()
    if (!mountRoot.canReadPermissionsDirectly(eelFsApi, localEel.fs, directAccess)) return null
    return directAccessPath
  }
  private fun <T : FileAttribute<*>> Array<T>.containsPosixAttribute(): Boolean {
    return any { attr -> attr.name().startsWith("posix:") }
  }


  protected fun <T> tryUseDirectAccess(source: Path, target: Path, vararg attrs: FileAttribute<*>, block: (Path, Path) -> T): T? {
    val sourceDirect = getDirectAccessPath(source, -attrs) ?: return null
    val targetDirect = getDirectAccessPath(target, -attrs) ?: return null

    try {
      return block(sourceDirect, targetDirect)
    }
    catch (e: IOException) {
      LOG.debug("Failed to use direct access for $sourceDirect -> $targetDirect, falling back to $delegate", e)
    }

    return null
  }

  private operator fun Array<out FileAttribute<*>>.unaryMinus(): EelMountRoot.DirectAccessOptions {
    return if (this.containsPosixAttribute()) {
      EelMountRoot.DirectAccessOptions.PosixAttributes
    }
    else EelMountRoot.DirectAccessOptions.BasicAttributes
  }

  protected fun <T> Path.tryUseDirectAccess(directAccess: EelMountRoot.DirectAccessOptions = EelMountRoot.DirectAccessOptions.BasicAttributes, block: (directPath: Path) -> T): T? {
    val directPath = getDirectAccessPath(this, directAccess) ?: return null

    try {
      return block(directPath)
    }
    catch (e: IOException) {
      LOG.debug("Failed to access $directPath directly, falling back to $delegate", e)
    }

    return null
  }

  override fun newInputStream(path: Path?, vararg options: OpenOption): InputStream {
    return path?.tryUseDirectAccess {
      Files.newInputStream(it, *options)
    } ?: delegate.newInputStream(path, *options)
  }

  override fun newOutputStream(path: Path?, vararg options: OpenOption): OutputStream {
    return path?.tryUseDirectAccess {
      Files.newOutputStream(it, *options)
    } ?: delegate.newOutputStream(path, *options)
  }

  override fun newByteChannel(path: Path?, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): SeekableByteChannel {
    return path?.tryUseDirectAccess(-attrs) {
      Files.newByteChannel(it, options, *attrs)
    } ?: delegate.newByteChannel(path, options, *attrs)
  }

  override fun newFileChannel(path: Path?, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): FileChannel {
    return path?.tryUseDirectAccess(-attrs) {
      FileChannel.open(it, options, *attrs)
    } ?: delegate.newFileChannel(path, options, *attrs)
  }

  override fun newAsynchronousFileChannel(path: Path?, options: Set<OpenOption?>?, executor: ExecutorService?, vararg attrs: FileAttribute<*>): AsynchronousFileChannel? {
    return path?.tryUseDirectAccess(-attrs) {
      AsynchronousFileChannel.open(it, options, executor, *attrs)
    } ?: delegate.newAsynchronousFileChannel(path, options, executor, *attrs)
  }

  override fun delete(path: Path?) {
    path?.tryUseDirectAccess {
      Files.delete(it)
    } ?: delegate.delete(path)
  }

  override fun createDirectory(dir: Path?, vararg attrs: FileAttribute<*>) {
    dir?.tryUseDirectAccess(-attrs) {
      Files.createDirectory(it, *attrs)
    } ?: delegate.createDirectory(dir, *attrs)
  }

  override fun deleteIfExists(path: Path?): Boolean {
    return path?.tryUseDirectAccess {
      Files.deleteIfExists(it)
    } ?: delegate.deleteIfExists(path)
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    return tryUseDirectAccess(path, path2) { s, t ->
      Files.isSameFile(s, t)
    } ?: delegate.isSameFile(path, path2)
  }

  override fun copy(source: Path?, target: Path?, vararg options: CopyOption) {
    if (source != null && target != null) {
      tryUseDirectAccess(source, target) { s, t -> Files.copy(s, t, *options) }?.let { return }
    }

    delegate.copy(source, target, *options)
  }

  override fun move(source: Path?, target: Path?, vararg options: CopyOption) {
    if (source != null && target != null) {
      tryUseDirectAccess(source, target) { s, t -> Files.move(s, t, *options) }?.let { return }
    }

    delegate.move(source, target, *options)
  }

  override fun createLink(link: Path?, existing: Path?) {
    if (link != null && existing != null) {
      tryUseDirectAccess(link, existing) { s, t -> Files.createLink(s, t) }?.let { return }
    }

    delegate.createLink(link, existing)
  }

  override fun createSymbolicLink(link: Path?, target: Path?, vararg attrs: FileAttribute<*>) {
    if (link != null && target != null) {
      tryUseDirectAccess(link, target, *attrs) { s, t -> Files.createSymbolicLink(s, t) }?.let { return }
    }

    delegate.createSymbolicLink(link, target, *attrs)
  }

  override fun newDirectoryStream(dir: Path?, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path?>? {
    val directDir = dir?.let(::getDirectAccessPath) ?: return delegate.newDirectoryStream(dir, filter)

    return try {
      val stream = Files.newDirectoryStream(directDir)

      object : DirectoryStream<Path?> {
        override fun iterator(): MutableIterator<Path?> {
          val it = stream.iterator()

          return object : MutableIterator<Path?> {
            override fun hasNext() = it.hasNext()
            override fun next(): Path {
              val next = it.next()
              val rel = directDir.relativize(next).toString()
              val containerPath = dir.resolve(rel)
              return if (filter == null || filter.accept(containerPath)) containerPath else next()
            }

            override fun remove() = it.remove()
          }
        }

        override fun close() = stream.close()
      }
    }
    catch (e: IOException) {
      LOG.debug("Failed to stream directory at $directDir, falling back to delegate", e)
      delegate.newDirectoryStream(dir, filter)
    }
  }

  override fun checkAccess(path: Path?, vararg modes: AccessMode) {
    path?.tryUseDirectAccess(EelMountRoot.DirectAccessOptions.PosixAttributesAndAllAccess) {
      it.fileSystem.provider().checkAccess(it, *modes)
    } ?: delegate.checkAccess(path, *modes)
  }

  override fun <A : BasicFileAttributes?> readAttributes(path: Path?, type: Class<A>, vararg options: LinkOption?): A? {
    // Optimize only if exactly BasicFileAttributes is requested.
    // We do NOT optimize PosixFileAttributes or other views that may expose container-specific metadata,
    // such as user/group ownership or permissions.
    if (type == BasicFileAttributes::class.java && options.all { linkOption -> linkOption == LinkOption.NOFOLLOW_LINKS }) {
      return path?.tryUseDirectAccess {
        Files.readAttributes(it, type, *options)
      } ?: delegate.readAttributes(path, type, *options)
    }

    return delegate.readAttributes(path, type, *options)
  }

  override fun isHidden(path: Path?): Boolean {
    // We don't optimize isHidden() because its behavior can vary between platforms,
    // and the result often depends on filesystem-specific attributes (e.g., dotfiles on Unix vs. 'hidden' flag on Windows).
    // Such metadata may not be reliably available or consistent between container and host.
    return delegate.isHidden(path)
  }

  // TODO: do we really should optimize this function?
  override fun readSymbolicLink(link: Path): Path {
    return delegate.readSymbolicLink(link)
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path?, type: Class<V?>?, vararg options: LinkOption?): V? {
    return delegate.getFileAttributeView(path, type, *options)
  }

  override fun readAttributes(path: Path?, attributes: String?, vararg options: LinkOption?): Map<String?, Any?>? {
    return delegate.readAttributes(path, attributes, *options)
  }

  override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
    delegate.setAttribute(path, attribute, value, *options)
  }

  companion object {
    private val LOG = logger<MountsAwareFileSystemProvider>()
  }

}

/**
 * A file system that uses MountsAwareFileSystemProvider to optimize access to files in Docker containers.
 */
@ApiStatus.Internal
class MountsAwareFileSystem(
  private val provider: MountsAwareFileSystemProvider,
  private val delegate: FileSystem,
) : DelegatingFileSystem<MountsAwareFileSystemProvider>() {
  override fun getDelegate(): FileSystem = delegate
  override fun toString(): String = """${javaClass.simpleName}($delegate)"""
  override fun close(): Unit = delegate.close()
  override fun provider(): MountsAwareFileSystemProvider = provider
}