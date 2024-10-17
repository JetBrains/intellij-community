// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.util.text.nullize
import java.io.File
import java.net.URI
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.Path
import kotlin.io.path.isSameFileAs
import kotlin.io.path.pathString

/**
 * The `RootAwarePath `class delegates all operations to the original IjentNioPath.
 * The root is used only as an information holder and for computing the `toUri` and `toString`.
 */
internal class IjentEphemeralRootAwarePath(val rootPath: Path, val originalPath: IjentNioPath) : Path {
  override fun getFileSystem(): FileSystem {
    return originalPath.fileSystem
  }

  override fun isAbsolute(): Boolean {
    return originalPath.isAbsolute
  }

  override fun getRoot(): Path? {
    return originalPath.root?.let { IjentEphemeralRootAwarePath(rootPath, it) }
  }

  override fun getFileName(): Path? {
    return originalPath.fileName
  }

  override fun getParent(): Path? {
    val parent = originalPath.parent ?: return null
    return IjentEphemeralRootAwarePath(rootPath, parent)
  }

  override fun getNameCount(): Int {
    return originalPath.nameCount
  }

  override fun getName(index: Int): Path {
    return originalPath.getName(index)
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.subpath(beginIndex, endIndex))
  }

  override fun startsWith(other: Path): Boolean {
    return originalPath.startsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun endsWith(other: Path): Boolean {
    return originalPath.endsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun normalize(): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.normalize())
  }

  override fun resolve(other: Path): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.resolve(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun relativize(other: Path): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.relativize(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun toUri(): URI {
    return rootPath.resolve(originalPath.pathString.removePrefix("/")).toUri()
  }

  override fun toAbsolutePath(): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.toRealPath(*options))
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    return originalPath.register(watcher, events, *modifiers)
  }

  override fun compareTo(other: Path): Int {
    return originalPath.compareTo(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun toFile(): File {
    return originalPath.toFile()
  }

  override fun equals(other: Any?): Boolean {
    return if (other is IjentEphemeralRootAwarePath) {
      other.rootPath == rootPath && other.originalPath == originalPath
    }
    else originalPath == other
  }

  override fun hashCode(): Int {
    var result = rootPath.hashCode()
    result = 31 * result + originalPath.hashCode()
    return result
  }

  override fun toString(): String {
    return rootPath.resolve(originalPath.pathString.removePrefix("/")).toString()
  }
}

internal class IjentEphemeralRootAwareFileSystemProvider(
  val root: Path,
  private val delegate: FileSystemProvider,
) : DelegatingFileSystemProvider<IjentEphemeralRootAwareFileSystemProvider, IjentEphemeralRootAwareFileSystem>(), RoutingAwareFileSystemProvider {
  override fun wrapDelegateFileSystem(delegateFs: FileSystem): IjentEphemeralRootAwareFileSystem {
    return IjentEphemeralRootAwareFileSystem(this, delegateFs)
  }

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider {
    return delegate
  }

  override fun toDelegatePath(path: Path?): Path? {
    if (path == null) return null

    if (path is IjentNioPath) {
      return IjentEphemeralRootAwarePath(root, path)
    }

    return path
  }

  override fun isSameFile(path: Path?, path2: Path?): Boolean {
    if (path == null || path2 == null) return false

    if (path is IjentEphemeralRootAwarePath && path2 is IjentEphemeralRootAwarePath) {
      return path.originalPath.isSameFileAs(path2.originalPath)
    }

    if (path.fileSystem !== path2.fileSystem) return false

    return super.isSameFile(path, path2)
  }

  override fun fromDelegatePath(path: Path?): Path? {
    if (path is IjentEphemeralRootAwarePath) {
      check(root === path.rootPath)
      return path.originalPath
    }

    return path
  }

  override fun canHandleRouting(): Boolean {
    return true
  }
}

/**
 * - getPath: returns a `RootAwarePath` when a prefix is present.
 * - getRootDirectories: returns *only* the `root`.
 */
internal class IjentEphemeralRootAwareFileSystem(
  private val rootAwareFileSystemProvider: IjentEphemeralRootAwareFileSystemProvider,
  private val originalFs: FileSystem,
) : DelegatingFileSystem<IjentEphemeralRootAwareFileSystemProvider>() {
  private val root: Path = rootAwareFileSystemProvider.root

  override fun getDelegate(): FileSystem {
    return originalFs
  }

  override fun getRootDirectories(): Iterable<Path?> {
    return listOf(Path(root.pathString))
  }

  override fun getPath(first: String, vararg more: String): Path {
    if (first.startsWith(root.pathString)) {
      val parts = more.flatMap { it.split("/") }.filter(String::isNotEmpty).toTypedArray()
      val ijentNioPath = originalFs.getPath(first.removePrefix(root.pathString).nullize() ?: "/", *parts) as IjentNioPath
      return IjentEphemeralRootAwarePath(root, ijentNioPath)
    }

    return super.getPath(first, *more)
  }

  override fun provider(): IjentEphemeralRootAwareFileSystemProvider {
    return rootAwareFileSystemProvider
  }
}