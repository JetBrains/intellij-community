// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.platform.core.nio.fs.*
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

private fun Path.unwrap(): Path = if (this is MultiRoutingFsPath) currentDelegate else this

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
@ApiStatus.Internal
class IjentEphemeralRootAwarePath(
  private val fileSystem: IjentEphemeralRootAwareFileSystem,
  val rootPath: Path,
  val originalPath: IjentNioPath,
) : Path, BasicFileAttributesHolder2.Impl(originalPath.getCachedFileAttributesAndWrapToDosAttributesAdapterIfNeeded()) {
  override fun getFileSystem(): FileSystem =
    fileSystem

  val actualPath = EelPathUtils.getActualPath(originalPath)

  override fun invalidate() {
    originalPath.invalidate()
    super.invalidate()
  }

  override fun isAbsolute(): Boolean {
    return originalPath.isAbsolute
  }

  override fun getRoot(): Path? {
    return originalPath.root?.let { IjentEphemeralRootAwarePath(fileSystem, rootPath, it) }
  }

  override fun getFileName(): Path? {
    return originalPath.fileName
  }

  override fun getParent(): Path? {
    val parent = originalPath.parent ?: return null
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, parent)
  }

  override fun getNameCount(): Int {
    return originalPath.nameCount
  }

  override fun getName(index: Int): Path {
    return originalPath.getName(index)
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.subpath(beginIndex, endIndex))
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
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.normalize())
  }

  override fun resolve(other: Path): Path {
    val other = other.unwrap()
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.resolve(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun relativize(other: Path): Path {
    val other = other.unwrap()
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.relativize(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun toUri(): URI {
    return rootPath.resolve(originalPath.pathString.removePrefix("/")).toUri()
  }

  override fun toAbsolutePath(): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    if (!isAbsolute) {
      return toAbsolutePath().toRealPath(*options)
    }

    if (normalize().toString() == rootPath.toString()) {
      return this
    }

    val ijentNioRealPath = if (originalPath != actualPath) {
      // `presentablePath` looks like `\\wsl$\distro\mnt\c`, any access to it from inside WSL throws permission denied errors.
      originalPath.normalize()
    }
    else {
      originalPath.toRealPath(*options)
    }

    return IjentEphemeralRootAwarePath(fileSystem, rootPath, ijentNioRealPath)
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier?): WatchKey {
    return actualPath.register(watcher, events, *modifiers)  // TODO Not well tested.
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

    if (other !is IjentEphemeralRootAwarePath) {
      return false
    }

    return this pathEqual other
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

private infix fun IjentEphemeralRootAwarePath.pathEqual(other: IjentEphemeralRootAwarePath): Boolean {
  if (fileSystem != other.fileSystem) {
    return false
  }

  if ((originalPath != actualPath || other.originalPath != other.actualPath) && (actualPath == other.actualPath) && (rootPath == other.rootPath)) {
    return false
  }

  val delegateIter = actualPath.iterator()
  val otherDelegateIter = other.actualPath.iterator()
  while (delegateIter.hasNext() && otherDelegateIter.hasNext()) {
    if (delegateIter.next() != otherDelegateIter.next()) {
      return false
    }
  }
  return !delegateIter.hasNext() && !otherDelegateIter.hasNext()
}

@ApiStatus.Internal
class IjentEphemeralRootAwareFileSystemProvider(
  val root: Path,
  private val ijentFsProvider: FileSystemProvider,
  private val originalFsProvider: FileSystemProvider,
  private val useRootDirectoriesFromOriginalFs: Boolean,
) : DelegatingFileSystemProvider<IjentEphemeralRootAwareFileSystemProvider, IjentEphemeralRootAwareFileSystem>(), RoutingAwareFileSystemProvider {
  private val originalFs = originalFsProvider.getFileSystem(URI("file:/"))

  override fun wrapDelegateFileSystem(delegateFs: FileSystem): IjentEphemeralRootAwareFileSystem {
    return IjentEphemeralRootAwareFileSystem(
      rootAwareFileSystemProvider = this,
      ijentFs = delegateFs,
      originalFs = originalFs,
      useRootDirectoriesFromOriginalFs = useRootDirectoriesFromOriginalFs
    )
  }

  override fun getScheme(): String? {
    return originalFsProvider.scheme
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    return when {
      SystemInfo.isWindows -> ijentFsProvider.readAttributesUsingDosAttributesAdapter(path, path.toIjentPath(), type, *options)
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
    return ijentFsProvider
  }

  override fun wrapDelegatePath(delegatePath: Path?): Path? {
    if (delegatePath == null) return null

    if (delegatePath is IjentNioPath) {
      return IjentEphemeralRootAwarePath(wrapDelegateFileSystem(delegatePath.fileSystem), root, delegatePath)
    }

    return delegatePath
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    if (path !is IjentEphemeralRootAwarePath) {
      if (path2 !is IjentEphemeralRootAwarePath) {
        throw ProviderMismatchException(
          "Neither $path (${path::class}) nor $path2 (${path2::class}) are ${IjentEphemeralRootAwarePath::class.java.name}"
        )
      }
      return isSameFile(path2, path)
    }

    if (path2 !is IjentEphemeralRootAwarePath) {
      return if (path.actualPath.fileSystem.provider() == path2.fileSystem.provider())
        Files.isSameFile(path.actualPath, path2)
      else
        false
    }

    if (path.actualPath == path.originalPath && path2.actualPath == path2.originalPath) {
      return Files.isSameFile(path.toIjentPath(), path2.toIjentPath())
    }

    if (path.actualPath.fileSystem.provider() == path2.actualPath.fileSystem.provider()) {
      return Files.isSameFile(path.actualPath, path2.actualPath)
    }

    return false
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
@ApiStatus.Internal
class IjentEphemeralRootAwareFileSystem(
  private val rootAwareFileSystemProvider: IjentEphemeralRootAwareFileSystemProvider,
  private val ijentFs: FileSystem,
  private val originalFs: FileSystem,
  private val useRootDirectoriesFromOriginalFs: Boolean,
) : DelegatingFileSystem<IjentEphemeralRootAwareFileSystemProvider>() {
  private val root: Path = rootAwareFileSystemProvider.root
  private val invariantSeparatorRootPathString = root.invariantSeparatorsPathString.removeSuffix("/")

  override fun getDelegate(): FileSystem {
    return ijentFs
  }

  override fun getRootDirectories(): Iterable<Path?> {
    return if (useRootDirectoriesFromOriginalFs) originalFs.rootDirectories else listOf(Path(root.pathString))
  }

  override fun close() {
    ijentFs.close()
  }

  override fun getPath(first: String, vararg more: String): Path {
    if (isPathUnderRoot(first)) {
      val parts = more.flatMap { it.split(root.fileSystem.separator) }.filter(String::isNotEmpty).toTypedArray()
      val ijentNioPath = ijentFs.getPath(relativizeToRoot(first), *parts) as IjentNioPath
      return IjentEphemeralRootAwarePath(this,root, ijentNioPath)
    }

    val delegateFs = getDelegate(first)
    val first = first.replace(originalFs.separator, delegateFs.separator)
    val more = more.toList().map { it.replace(originalFs.separator, delegateFs.separator) }.toTypedArray()
    return super.getPath(first, *more)
  }

  override fun provider(): IjentEphemeralRootAwareFileSystemProvider {
    return rootAwareFileSystemProvider
  }

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher = originalFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService = originalFs.userPrincipalLookupService

  override fun newWatchService(): WatchService = originalFs.newWatchService()

  override fun getFileStores(): Iterable<FileStore> = originalFs.fileStores + ijentFs.fileStores

  override fun isOpen(): Boolean = true

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String = originalFs.separator

  override fun supportedFileAttributeViews(): Set<String> = buildSet {
    addAll(originalFs.supportedFileAttributeViews())
    addAll(ijentFs.supportedFileAttributeViews())
  }

  private fun isPathUnderRoot(path: String): Boolean {
    return toSystemIndependentName(path).startsWith(invariantSeparatorRootPathString)
  }

  // TODO: improve this function when we will support ijent on windows
  private fun relativizeToRoot(path: String): String {
    return (toSystemIndependentName(path).removePrefix(invariantSeparatorRootPathString)).nullize() ?: "/"
  }
}