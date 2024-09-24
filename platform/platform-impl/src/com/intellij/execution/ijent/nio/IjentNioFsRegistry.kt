// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.net.URI
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.pathString

/**
 * Service for registering custom file systems, typically remote ones.
 *
 *  Usage:
 *
 * ```kotlin
 * val ijentRegistry = IjentNioFsRegistry.instance()
 * val ijentPath = ijentRegistry.registerFs(ijentApi, <root>, <authority (wsl/docker/etc.)>)
 * ```
 */
// TODO: merge it with IjentWslNioFsToggler/IjentNioFsStrategy
@ApiStatus.Internal
@Service
class IjentNioFsRegistry private constructor() {
  companion object {
    suspend fun instanceAsync(): IjentNioFsRegistry = serviceAsync()
    fun instance(): IjentNioFsRegistry = service()
  }

  fun isAvailable() = registry != null

  fun registerFs(ijent: IjentApi, root: String, authority: String): Path {
    registry ?: error("Not available")

    val uri = URI("ijent", authority, root, null, null)

    try {
      IjentNioFileSystemProvider.getInstance().newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    registry.computeIfAbsent(root) {
      // Compute a path before custom fs registration. Usually should represent a non-existent local path
      val localPath = Path(root).also { check(!it.exists()) }

      RootAwareFileSystemProvider(
        root = localPath,
        delegate = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
      ).getFileSystem(uri)
    }

    // TODO: IjentApi should contains something like onTerminated(block: () -> Unit)
    // ijent.onTerminated {
    //    registry.remove(root).close()
    //}

    // Compute a path after registration
    return Path(root)
  }

  private val registry = run {
    val defaultProvider = FileSystems.getDefault().provider()

    if (defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name) {
      FileSystemsRegistry(defaultProvider)
    }
    else {
      logger<IjentNioFsRegistry>().warn(
        "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"
      )
      null
    }
  }
}

/**
 * The `RootAwarePath `class delegates all operations to the original IjentNioPath.
 * The root is used only as an information holder and for computing the `toUri` and `toString`.
 */
private class RootAwarePath(val rootPath: Path, val originalPath: IjentNioPath) : Path {
  override fun getFileSystem(): FileSystem {
    return originalPath.fileSystem
  }

  override fun isAbsolute(): Boolean {
    return originalPath.isAbsolute
  }

  override fun getRoot(): Path? {
    return originalPath.root
  }

  override fun getFileName(): Path? {
    return originalPath.fileName
  }

  override fun getParent(): Path? {
    val parent = originalPath.parent ?: return null
    return RootAwarePath(rootPath, parent)
  }

  override fun getNameCount(): Int {
    return originalPath.nameCount
  }

  override fun getName(index: Int): Path {
    return originalPath.getName(index)
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    return RootAwarePath(rootPath, originalPath.subpath(beginIndex, endIndex))
  }

  override fun startsWith(other: Path): Boolean {
    return originalPath.startsWith(if (other is RootAwarePath) other.originalPath else other)
  }

  override fun endsWith(other: Path): Boolean {
    return originalPath.endsWith(if (other is RootAwarePath) other.originalPath else other)
  }

  override fun normalize(): Path {
    return RootAwarePath(rootPath, originalPath.normalize())
  }

  override fun resolve(other: Path): Path {
    return RootAwarePath(rootPath, originalPath.resolve(if (other is RootAwarePath) other.originalPath else other))
  }

  override fun relativize(other: Path): Path {
    return RootAwarePath(rootPath, originalPath.relativize(if (other is RootAwarePath) other.originalPath else other))
  }

  override fun toUri(): URI {
    return rootPath.resolve(originalPath.pathString.removePrefix("/")).toUri()
  }

  override fun toAbsolutePath(): Path {
    return RootAwarePath(rootPath, originalPath.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    return RootAwarePath(rootPath, originalPath.toRealPath(*options))
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    return originalPath.register(watcher, events, *modifiers)
  }

  override fun compareTo(other: Path): Int {
    return originalPath.compareTo(if (other is RootAwarePath) other.originalPath else other)
  }

  override fun equals(other: Any?): Boolean {
    return originalPath == if (other is RootAwarePath) other.originalPath else other
  }

  override fun toFile(): File {
    return originalPath.toFile()
  }

  override fun hashCode(): Int {
    return originalPath.hashCode()
  }

  override fun toString(): String {
    return rootPath.resolve(originalPath.pathString.removePrefix("/")).toString()
  }
}

private class RootAwareFileSystemProvider(
  val root: Path,
  private val delegate: FileSystemProvider,
) : DelegatingFileSystemProvider<RootAwareFileSystemProvider, RootAwareFileSystem>(), RoutingAwareFileSystemProvider {
  override fun wrapDelegateFileSystem(delegateFs: FileSystem): RootAwareFileSystem {
    return RootAwareFileSystem(this, delegateFs)
  }

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider {
    return delegate
  }

  override fun toDelegatePath(path: Path?): Path? {
    if (path == null) return null

    if (path is IjentNioPath) {
      return RootAwarePath(root, path)
    }

    return path
  }

  override fun isSameFile(path: Path?, path2: Path?): Boolean {
    if (path == null || path2 == null) return false

    if (path is RootAwarePath && path2 is RootAwarePath) {
      return path.originalPath.isSameFileAs(path2.originalPath)
    }

    if (path.fileSystem !== path2.fileSystem) return false

    return super.isSameFile(path, path2)
  }

  override fun fromDelegatePath(path: Path?): Path? {
    if (path is RootAwarePath) {
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
private class RootAwareFileSystem(
  private val rootAwareFileSystemProvider: RootAwareFileSystemProvider,
  private val originalFs: FileSystem,
) : DelegatingFileSystem<RootAwareFileSystemProvider>() {
  private val root: Path = rootAwareFileSystemProvider.root

  override fun getDelegate(): FileSystem {
    return originalFs
  }

  override fun getRootDirectories(): Iterable<Path?> {
    return listOf(Path(root.pathString))
  }

  override fun getPath(first: String, vararg more: String): Path {
    if (first.startsWith(root.pathString)) {
      val ijentNioPath = originalFs.getPath(first.removePrefix(root.pathString).nullize() ?: "/", *more) as IjentNioPath
      return RootAwarePath(root, ijentNioPath)
    }

    return super.getPath(first, *more)
  }

  override fun provider(): RootAwareFileSystemProvider {
    return rootAwareFileSystemProvider
  }
}

private class FileSystemsRegistry(private val multiRoutingFileSystemProvider: FileSystemProvider) {
  private val own: MutableMap<String, FileSystem> = ConcurrentHashMap()

  fun computeIfAbsent(root: String, compute: (String) -> FileSystem) {
    MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, root, true, true) { _, _ ->
      own.computeIfAbsent(root, compute)
    }
  }
}