// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap

class TransitionalIjentWslFileSystemProvider(
  private val localFsProvider: FileSystemProvider,
  private val ijentFsProvider: FileSystemProvider,
) : FileSystemProvider() {
  override fun getScheme(): String =
    localFsProvider.scheme

  private val fileSystems = ConcurrentHashMap<URI, TransitionalIjentWslFileSystem>()

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): FileSystem =
    fileSystems.compute(uri) { _, old ->
      if (old != null) throw FileSystemAlreadyExistsException()
      TransitionalIjentWslFileSystem(
        provider = this,
        localFs = localFsProvider.newFileSystem(uri, env),
        ijentFs = ijentFsProvider.newFileSystem(uri, env),
      )
    }!!

  override fun getFileSystem(uri: URI): FileSystem =
    fileSystems[uri] ?: throw FileSystemNotFoundException()

  override fun getPath(uri: URI): Path =
    localFsProvider.getPath(uri)

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel =
    localFsProvider.newByteChannel(path, options, *attrs)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
    ijentFsProvider.newDirectoryStream(dir, filter)

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    localFsProvider.createDirectory(dir, *attrs)
  }

  override fun delete(path: Path) {
    localFsProvider.delete(path)
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    localFsProvider.copy(source, target, *options)
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    localFsProvider.move(source, target, *options)
  }

  override fun isSameFile(path: Path, path2: Path): Boolean =
    localFsProvider.isSameFile(path, path2)

  override fun isHidden(path: Path): Boolean =
    localFsProvider.isHidden(path)

  override fun getFileStore(path: Path): FileStore =
    localFsProvider.getFileStore(path)

  override fun checkAccess(path: Path, vararg modes: AccessMode?) {
    ijentFsProvider.checkAccess(path, *modes)
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>?, vararg options: LinkOption?): V =
    localFsProvider.getFileAttributeView(path, type, *options)

  override fun <A : BasicFileAttributes?> readAttributes(path: Path, type: Class<A>?, vararg options: LinkOption?): A =
    localFsProvider.readAttributes(path, type, *options)

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption?): MutableMap<String, Any> =
    localFsProvider.readAttributes(path, attributes, *options)

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    localFsProvider.setAttribute(path, attribute, value, *options)
  }
}