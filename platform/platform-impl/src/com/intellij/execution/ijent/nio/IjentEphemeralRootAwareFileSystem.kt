// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.platform.core.nio.fs.*
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.util.text.nullize
import java.io.File
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isSameFileAs
import kotlin.io.path.pathString

private fun Path.unwrap(): Path = if (this is MultiRoutingFsPath) delegate else this

private fun Path.toIjentPathOrNull(): IjentNioPath? = when (val path = unwrap()) {
  is IjentEphemeralRootAwarePath -> path.originalPath
  is IjentNioPath -> path
  else -> null
}

private fun Path.toIjentPath(): IjentNioPath = toIjentPathOrNull() ?: throw IllegalArgumentException("Cannot convert $this to IjentNioPath")

private fun Path.toOriginalPath(): Path = toIjentPathOrNull() ?: this

/**
 * The `RootAwarePath `class delegates all operations to the original IjentNioPath.
 * The root is used only as an information holder and for computing the `toUri` and `toString`.
 */
@Suppress("NAME_SHADOWING")
internal class IjentEphemeralRootAwarePath(
  val rootPath: Path,
  val originalPath: IjentNioPath,
) : Path, BasicFileAttributesHolder2.Impl(originalPath.getCachedFileAttributesAndWrapToDosAttributesAdapterIfNeeded()) {
  override fun getFileSystem(): FileSystem {
    return originalPath.fileSystem
  }

  override fun invalidate() {
    originalPath.invalidate()
    super.invalidate()
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
    val other = other.unwrap()
    return originalPath.startsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun endsWith(other: Path): Boolean {
    val other = other.unwrap()
    return originalPath.endsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun normalize(): Path {
    return IjentEphemeralRootAwarePath(rootPath, originalPath.normalize())
  }

  override fun resolve(other: Path): Path {
    val other = other.unwrap()
    return IjentEphemeralRootAwarePath(rootPath, originalPath.resolve(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun relativize(other: Path): Path {
    val other = other.unwrap()
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
    val other = other.unwrap()
    return originalPath.compareTo(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun toFile(): File {
    return originalPath.toFile()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Path) return false

    val other = other.unwrap()

    return if (other is IjentEphemeralRootAwarePath) other.rootPath == rootPath && other.originalPath == originalPath else originalPath == other
  }

  override fun hashCode(): Int {
    var result = rootPath.hashCode()
    result = 31 * result + originalPath.hashCode()
    return result
  }

  override fun toString(): String {
    return if (isAbsolute) {
      rootPath.resolve(originalPath.pathString.removePrefix("/")).toString()
    }
    else {
      originalPath.toString()
    }
  }
}

internal class IjentEphemeralRootAwareFileSystemProvider(
  val root: Path,
  private val delegate: FileSystemProvider,
) : DelegatingFileSystemProvider<IjentEphemeralRootAwareFileSystemProvider, IjentEphemeralRootAwareFileSystem>(), RoutingAwareFileSystemProvider {
  override fun wrapDelegateFileSystem(delegateFs: FileSystem): IjentEphemeralRootAwareFileSystem {
    return IjentEphemeralRootAwareFileSystem(this, delegateFs)
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    return when {
      SystemInfo.isWindows -> delegate.readAttributesUsingDosAttributesAdapter(path, path.toIjentPath(), type, *options)
      else -> super.readAttributes(path, type, *options)
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    if (source.getEelDescriptor() == target.getEelDescriptor()) {
      super.copy(source, target, *options)
    }
    else {
      EelPathUtils.walkingTransfer(source.toOriginalPath(), target.toOriginalPath(), removeSource = false, copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    if (source.getEelDescriptor() == target.getEelDescriptor()) {
      super.move(source, target, *options)
    }
    else {
      EelPathUtils.walkingTransfer(source.toOriginalPath(), target.toOriginalPath(), removeSource = true, copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
    }
  }

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider {
    return delegate
  }

  override fun wrapDelegatePath(delegatePath: Path?): Path? {
    if (delegatePath == null) return null

    if (delegatePath is IjentNioPath) {
      return IjentEphemeralRootAwarePath(root, delegatePath)
    }

    return delegatePath
  }

  override fun isSameFile(path: Path?, path2: Path?): Boolean {
    if (path == null || path2 == null) return false

    if (path is IjentEphemeralRootAwarePath && path2 is IjentEphemeralRootAwarePath) {
      return path.originalPath.isSameFileAs(path2.originalPath)
    }

    if (path.fileSystem !== path2.fileSystem) return false

    return super.isSameFile(path, path2)
  }

  override fun toDelegatePath(path: Path?): Path? {
    if (path is IjentEphemeralRootAwarePath) {
      check(root === path.rootPath)
      return path.originalPath
    }

    return path
  }

  override fun canHandleRouting(path: Path): Boolean {
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
  private val invariantSeparatorRootPathString = root.invariantSeparatorsPathString.removeSuffix("/")

  override fun getDelegate(): FileSystem {
    return originalFs
  }

  override fun getRootDirectories(): Iterable<Path?> {
    return listOf(Path(root.pathString))
  }

  override fun getPath(first: String, vararg more: String): Path {
    if (isPathUnderRoot(first)) {
      val parts = more.flatMap { it.split(root.fileSystem.separator) }.filter(String::isNotEmpty).toTypedArray()
      val ijentNioPath = originalFs.getPath(relativizeToRoot(first), *parts) as IjentNioPath
      return IjentEphemeralRootAwarePath(root, ijentNioPath)
    }

    return super.getPath(first, *more)
  }

  override fun provider(): IjentEphemeralRootAwareFileSystemProvider {
    return rootAwareFileSystemProvider
  }

  private fun isPathUnderRoot(path: String): Boolean {
    return toSystemIndependentName(path).startsWith(invariantSeparatorRootPathString)
  }

  // TODO: improve this function when we will support ijent on windows
  private fun relativizeToRoot(path: String): String {
    return (toSystemIndependentName(path).removePrefix(invariantSeparatorRootPathString)).nullize() ?: "/"
  }
}