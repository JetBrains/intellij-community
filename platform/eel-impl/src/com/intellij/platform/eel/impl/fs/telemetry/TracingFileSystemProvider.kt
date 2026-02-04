// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import org.jetbrains.annotations.ApiStatus
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider

/**
 * A contract of this file system: every instance of [TracingFileSystemProvider] and [TracingFileSystem] writes to
 * the same statistics collectors.
 * Given that [delegate] is the same, measures from instances of `TracingFileSystemProvider(delegate)` are written into the single place.
 * It's possible to get rid of this contract, but usages should be refactored then.
 */
// TODO There should be an implementation for Path, to meet the contract `fsp.getPath(..).fileSystem.provider() === fsp`
@ApiStatus.Internal
class TracingFileSystemProvider(
  val delegate: FileSystemProvider,
  val spanNamePrefix: String = "",
) : DelegatingFileSystemProvider<TracingFileSystemProvider, TracingFileSystem>(), RoutingAwareFileSystemProvider {
  override fun wrapDelegateFileSystem(delegateFs: FileSystem): TracingFileSystem =
    TracingFileSystem(this, delegateFs, spanNamePrefix)

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider = delegate

  override fun wrapDelegatePath(delegatePath: Path?): Path? = delegatePath

  override fun toDelegatePath(path: Path?): Path? = path

  override fun toString(): String = """${javaClass.simpleName}($delegate)"""

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    Measurer.measure(Measurer.Operation.providerNewByteChannel, spanNamePrefix) {
      super.newByteChannel(path, options, *attrs)
    }.traced(spanNamePrefix)

  override fun newFileChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): FileChannel =
    Measurer.measure(Measurer.Operation.providerNewFileChannel, spanNamePrefix) {
      super.newFileChannel(path, options, *attrs)
    }.traced(spanNamePrefix)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    TracingDirectoryStream<Path>(Measurer.measure(Measurer.Operation.providerNewDirectoryStream, spanNamePrefix) {
      super.newDirectoryStream(dir, filter)
    }, spanNamePrefix)

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    Measurer.measure(Measurer.Operation.providerCreateDirectory, spanNamePrefix) {
      super.createDirectory(dir, *attrs)
    }
  }

  override fun delete(path: Path) {
    Measurer.measure(Measurer.Operation.providerDelete, spanNamePrefix) {
      super.delete(path)
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    Measurer.measure(Measurer.Operation.providerCopy, spanNamePrefix) {
      super.copy(source, target, *options)
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    Measurer.measure(Measurer.Operation.providerMove, spanNamePrefix) {
      super.move(source, target, *options)
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean =
    Measurer.measure(Measurer.Operation.providerIsSameFile, spanNamePrefix) {
      super.isSameFile(path, path2)
    }

  override fun isHidden(path: Path): Boolean =
    Measurer.measure(Measurer.Operation.providerIsHidden, spanNamePrefix) {
      super.isHidden(path)
    }

  override fun getFileStore(path: Path): FileStore =
    Measurer.measure(Measurer.Operation.providerGetFileStore, spanNamePrefix) {
      super.getFileStore(path)
    }

  override fun checkAccess(path: Path, vararg modes: AccessMode?) {
    Measurer.measure(Measurer.Operation.providerCheckAccess, spanNamePrefix) {
      super.checkAccess(path, *modes)
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path?, type: Class<V>?, vararg options: LinkOption?): V =
    Measurer.measure(Measurer.Operation.providerGetFileAttributeView, spanNamePrefix) {
      super.getFileAttributeView(path, type, *options)
    }

  override fun <A : BasicFileAttributes?> readAttributes(path: Path?, type: Class<A>?, vararg options: LinkOption?): A =
    Measurer.measure(Measurer.Operation.providerReadAttributes, spanNamePrefix) {
      super.readAttributes(path, type, *options)
    }

  override fun readAttributes(path: Path?, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    Measurer.measure(Measurer.Operation.providerReadAttributes, spanNamePrefix) {
      super.readAttributes(path, attributes, *options)
    }

  override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
    Measurer.measure(Measurer.Operation.providerSetAttribute, spanNamePrefix) {
      super.setAttribute(path, attribute, value, *options)
    }
  }

  override fun createSymbolicLink(link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) {
    Measurer.measure(Measurer.Operation.providerCreateSymbolicLink, spanNamePrefix) {
      super.createSymbolicLink(link, target, *attrs)
    }
  }

  override fun createLink(link: Path?, existing: Path?) {
    Measurer.measure(Measurer.Operation.providerCreateLink, spanNamePrefix) {
      super.createLink(link, existing)
    }
  }

  override fun deleteIfExists(path: Path?): Boolean =
    Measurer.measure(Measurer.Operation.providerDeleteIfExists, spanNamePrefix) {
      super.deleteIfExists(path)
    }

  override fun readSymbolicLink(link: Path?): Path =
    Measurer.measure(Measurer.Operation.providerReadSymbolicLink, spanNamePrefix) {
      super.readSymbolicLink(link)
    }
}