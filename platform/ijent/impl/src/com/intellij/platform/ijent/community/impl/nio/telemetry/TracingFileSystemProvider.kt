// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.Measurer.Operation.*
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
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
class TracingFileSystemProvider(
  val delegate: FileSystemProvider,
) : DelegatingFileSystemProvider<TracingFileSystemProvider, TracingFileSystem>(), RoutingAwareFileSystemProvider {
  override fun wrapDelegateFileSystem(delegateFs: FileSystem): TracingFileSystem =
    TracingFileSystem(this, delegateFs)

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider = delegate

  override fun toDelegatePath(path: Path?): Path? = path

  override fun fromDelegatePath(path: Path?): Path? = path

  override fun toString(): String = """${javaClass.simpleName}($delegate)"""

  override fun canHandleRouting(): Boolean =
    (delegate as? RoutingAwareFileSystemProvider)?.canHandleRouting() == true

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    TracingSeekableByteChannel(this, Measurer.measure(providerNewByteChannel) {
      super.newByteChannel(path, options, *attrs)
    })

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    TracingDirectoryStream<Path>(Measurer.measure(providerNewDirectoryStream) {
      super.newDirectoryStream(dir, filter)
    })

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    Measurer.measure(providerCreateDirectory) {
      super.createDirectory(dir, *attrs)
    }
  }

  override fun delete(path: Path) {
    Measurer.measure(providerDelete) {
      super.delete(path)
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    Measurer.measure(providerCopy) {
      super.copy(source, target, *options)
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    Measurer.measure(providerMove) {
      super.move(source, target, *options)
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean =
    Measurer.measure(providerIsSameFile) {
      super.isSameFile(path, path2)
    }

  override fun isHidden(path: Path): Boolean =
    Measurer.measure(providerIsHidden) {
      super.isHidden(path)
    }

  override fun getFileStore(path: Path): FileStore =
    Measurer.measure(providerGetFileStore) {
      super.getFileStore(path)
    }

  override fun checkAccess(path: Path, vararg modes: AccessMode?) {
    Measurer.measure(providerCheckAccess) {
      super.checkAccess(path, *modes)
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path?, type: Class<V>?, vararg options: LinkOption?): V =
    Measurer.measure(providerGetFileAttributeView) {
      super.getFileAttributeView(path, type, *options)
    }

  override fun <A : BasicFileAttributes?> readAttributes(path: Path?, type: Class<A>?, vararg options: LinkOption?): A =
    Measurer.measure(providerReadAttributes) {
      super.readAttributes(path, type, *options)
    }

  override fun readAttributes(path: Path?, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    Measurer.measure(providerReadAttributes) {
      super.readAttributes(path, attributes, *options)
    }

  override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
    Measurer.measure(providerSetAttribute) {
      super.setAttribute(path, attribute, value, *options)
    }
  }

  override fun createSymbolicLink(link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) {
    Measurer.measure(providerCreateSymbolicLink) {
      super.createSymbolicLink(link, target, *attrs)
    }
  }

  override fun createLink(link: Path?, existing: Path?) {
    Measurer.measure(providerCreateLink) {
      super.createLink(link, existing)
    }
  }

  override fun deleteIfExists(path: Path?): Boolean =
    Measurer.measure(providerDeleteIfExists) {
      super.deleteIfExists(path)
    }

  override fun readSymbolicLink(link: Path?): Path =
    Measurer.measure(providerReadSymbolicLink) {
      super.readSymbolicLink(link)
    }
}