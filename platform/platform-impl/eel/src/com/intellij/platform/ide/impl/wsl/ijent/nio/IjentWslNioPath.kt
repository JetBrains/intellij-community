// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio

import com.intellij.execution.ijent.nio.IjentNioPosixFileAttributesWithDosAdapter
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.eel.provider.utils.EelPathUtils.getActualPath
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.*

@ApiStatus.Internal
class IjentWslNioPath(
  private val fileSystem: IjentWslNioFileSystem,

  /**
   * The path as the user or the tool sees it. A direct representation of some path read from some file, some tool, given by a user, etc.
   */
  val presentablePath: Path,

  cachedAttributes: IjentNioPosixFileAttributesWithDosAdapter?,
) : Path, BasicFileAttributesHolder2.Impl(cachedAttributes) {
  init {
    require(presentablePath !is IjentWslNioPath) { "IjentWslNioPath should be a wrapper over other instances of path, namely WindowsPath or IjentNioPath" }
  }

  val actualPath: Path = getActualPath(presentablePath)

  override fun getFileSystem(): IjentWslNioFileSystem = fileSystem

  override fun isAbsolute(): Boolean = presentablePath.isAbsolute

  override fun getRoot(): IjentWslNioPath? = presentablePath.root?.toIjentWslPath()

  override fun getFileName(): IjentWslNioPath? = presentablePath.fileName?.toIjentWslPath()

  override fun getParent(): IjentWslNioPath? = presentablePath.parent?.toIjentWslPath()

  override fun getNameCount(): Int = presentablePath.nameCount

  override fun getName(index: Int): IjentWslNioPath = presentablePath.getName(index).toIjentWslPath()

  override fun subpath(beginIndex: Int, endIndex: Int): IjentWslNioPath = presentablePath.subpath(beginIndex, endIndex).toIjentWslPath()

  override fun startsWith(other: Path): Boolean = presentablePath.startsWith(other.toOriginalPath())

  override fun endsWith(other: Path): Boolean = presentablePath.endsWith(other.toOriginalPath())

  override fun normalize(): IjentWslNioPath = presentablePath.normalize().toIjentWslPath()

  override fun resolve(other: Path): IjentWslNioPath = presentablePath.resolve(other.toOriginalPath()).toIjentWslPath()

  override fun relativize(other: Path): IjentWslNioPath = presentablePath.relativize(other.toOriginalPath()).toIjentWslPath()

  override fun toUri(): URI = presentablePath.toUri()

  override fun toAbsolutePath(): IjentWslNioPath = presentablePath.toAbsolutePath().toIjentWslPath()

  override fun toRealPath(vararg options: LinkOption): IjentWslNioPath {
    if (!isAbsolute) {
      return toAbsolutePath().toRealPath(*options)
    }

    when (normalize().toString()) {
      "\\\\wsl$\\${fileSystem.wslId}\\", "\\\\wsl.localhost\\${fileSystem.wslId}\\" -> {
        return this
      }
    }

    val ijentNioPath = fileSystem.provider().toIjentNioPath(this)
    val ijentNioRealPath =
      if (presentablePath != actualPath) {
        // `presentablePath` looks like `\\wsl$\distro\mnt\c`, any access to it from inside WSL throws permission denied errors.
        ijentNioPath.normalize()
      }
      else {
        ijentNioPath.toRealPath(*options)
      }
    val originalPath = fileSystem.provider().toOriginalPath(
      path = ijentNioRealPath,
      notation = root.toString().removePrefix("\\\\").substringBefore('\\'),
    )
    return originalPath.toIjentWslPath()
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>?>?, vararg modifiers: WatchEvent.Modifier?): WatchKey =
    actualPath.register(watcher, events, *modifiers)  // TODO Not well tested.

  override fun compareTo(other: Path): Int = presentablePath.compareTo(other.toOriginalPath())

  private fun Path.toIjentWslPath(): IjentWslNioPath =
    IjentWslNioPath(this@IjentWslNioPath.fileSystem, this, null)

  private fun Path.toOriginalPath(): Path =
    if (this is IjentWslNioPath) this.presentablePath
    else this

  override fun toString(): String = presentablePath.toString()

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is IjentWslNioPath -> false
    else -> this wslPathEqual other
  }

  override fun hashCode(): Int =
    fileSystem.hashCode() + 31 * presentablePath.hashCode()
}

private infix fun IjentWslNioPath.wslPathEqual(other: IjentWslNioPath): Boolean {
  if (fileSystem != other.fileSystem) {
    return false
  }

  if (
    (presentablePath != actualPath || other.presentablePath != other.actualPath)
    && actualPath == other.actualPath
  ) {
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