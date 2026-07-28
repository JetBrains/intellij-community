// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio

import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import com.intellij.platform.eel.provider.utils.EelPathUtils.getActualPath
import com.intellij.platform.eel.provider.utils.impl.ijentToLocal
import com.intellij.platform.eel.provider.utils.impl.localToIjent
import com.intellij.platform.ide.impl.wsl.WSL_PREFIXES
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.platform.ijent.community.impl.nio.fs.IjentNioPosixFileAttributesWithDosAdapter
import java.net.URI
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.pathString

internal class IjentWslNioPath(
  private val fileSystem: IjentWslNioFileSystem,

  /**
   * The path as the user or the tool sees it. A direct representation of some path read from some file, some tool, given by a user, etc.
   */
  val presentablePath: Path,

  cachedAttributes: IjentNioPosixFileAttributesWithDosAdapter?,
) : Path, BasicFileAttributesHolder2.Impl(cachedAttributes) {
  init {
    // `MultiRoutingFsPath` is rejected as well: it may delegate to an `IjentWslNioPath`, and such nesting silently breaks
    // `equals`, `hashCode` and every call that passes `presentablePath` to the original (Windows) file system provider.
    require(presentablePath !is IjentWslNioPath && presentablePath !is MultiRoutingFsPath) {
      "IjentWslNioPath should be a wrapper over other instances of path, namely WindowsPath or IjentNioPath," +
      " but got ${presentablePath.javaClass.name}: $presentablePath"
    }
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

  override fun resolve(other: Path): IjentWslNioPath {
    val otherPath = other.toSameFlavourAsPresentablePath()
    // `Path.resolve` returns `other` as is when it is absolute, but the result still has to be a path of this file system.
    return if (otherPath.isAbsolute) otherPath.toIjentWslPath()
    else presentablePath.resolve(otherPath).toIjentWslPath()
  }

  override fun relativize(other: Path): IjentWslNioPath {
    if (isAbsolute != other.isAbsolute) {
      throw IllegalArgumentException("Tried to relativize a relative and an absolute path: `$this` and `$other`." + " Check for possible confusion." + " Maybe some code up the call stack tried to use a path from the Linux machine as a WSL path for Windows.")
    }
    return presentablePath.relativize(other.toSameFlavourAsPresentablePath()).toIjentWslPath()
  }

  override fun toUri(): URI = presentablePath.toUri()

  override fun toAbsolutePath(): IjentWslNioPath = presentablePath.toAbsolutePath().toIjentWslPath()

  override fun toRealPath(vararg options: LinkOption): IjentWslNioPath {
    if (!isAbsolute) {
      return toAbsolutePath().toRealPath(*options)
    }

    // Comparison is done on strings rather than on paths: `normalize()` returns an IjentWslNioPath, which is never equal to a path
    // produced by any other filesystem.
    val normalized = normalize().toString()
    if (WSL_PREFIXES.any { normalized == "\\\\$it\\${fileSystem.wslId}\\" }) {
      return this
    }

    val ijentNioPath = fileSystem.provider().toIjentNioPath(this)
    val ijentNioRealPath = if (presentablePath != actualPath) {
      // `presentablePath` looks like `\\wsl$\distro\mnt\c`, any access to it from inside WSL throws permission denied errors.
      ijentNioPath.normalize()
    }
    else {
      ijentNioPath.toRealPath(*options)
    }
    val originalPath = fileSystem.provider().toOriginalPath(path = ijentNioRealPath, notation = presentableNotation)
    return originalPath.toIjentWslPath()
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier?): WatchKey {
    val ijentPath: Path = fileSystem.provider().toIjentNioPath(this)
    @Suppress("UNCHECKED_CAST") return ijentPath.register(watcher, events, *modifiers.filterNotNull().toTypedArray())
  }

  override fun compareTo(other: Path): Int = presentablePath.compareTo(other.toOriginalPath())

  private fun Path.toIjentWslPath(): IjentWslNioPath =
    this as? IjentWslNioPath ?: IjentWslNioPath(this@IjentWslNioPath.fileSystem, this, null)

  private fun Path.toOriginalPath(): Path = when (this) {
    is IjentWslNioPath -> this.presentablePath.toOriginalPath()
    // A path of the routing file system may delegate to a path of this very file system, so it is not a foreign path.
    is MultiRoutingFsPath -> this.initialDelegate.toOriginalPath()
    else -> this
  }

  /**
   * Returns [this] converted to the same kind of path as [presentablePath] (i.e. `WindowsPath` or [IjentNioPath]),
   * so that both can be used together in a single [Path] operation.
   * Special chars like `:` are mapped in the direction that [presentablePath] requires, see [ijentToLocal] and [localToIjent].
   *
   * This is the same trick as [MultiRoutingFsPath.toSameTypeAsDelegate]:
   * it is always the *argument* that is brought to the flavour of the receiver, never the other way round.
   */
  private fun Path.toSameFlavourAsPresentablePath(): Path {
    val originalPath = toOriginalPath()
    return when {
      presentablePath.javaClass == originalPath.javaClass -> originalPath
      // An absolute IJent path has no `\\wsl$\distro\` prefix, and that prefix must use the same notation as this path.
      originalPath is IjentNioPath && originalPath.isAbsolute ->
        this@IjentWslNioPath.fileSystem.provider().toOriginalPath(originalPath, presentableNotation)

      originalPath is IjentNioPath ->
        presentablePath.fileSystem.getPath(ijentToLocal(originalPath.pathString))

      presentablePath is IjentNioPath ->
        presentablePath.fileSystem.getPath(localToIjent(originalPath.pathString.replace('\\', '/')))

      else ->
        presentablePath.fileSystem.getPath(originalPath.pathString)
    }
  }

  /**
   * `wsl$` or `wsl.localhost`: the notation used by this path.
   * These two must never be mixed within one path, see [com.intellij.platform.eel.provider.asNioPath].
   */
  private val presentableNotation: String
    get() = fileSystem.provider().notationFromRoot(presentablePath.root?.toString() ?: "")

  override fun toString(): String = presentablePath.toString()

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is IjentWslNioPath -> false
    else -> this wslPathEqual other
  }

  override fun hashCode(): Int = fileSystem.hashCode() + 31 * presentablePath.hashCode()
}

private infix fun IjentWslNioPath.wslPathEqual(other: IjentWslNioPath): Boolean {
  if (fileSystem != other.fileSystem) {
    return false
  }

  if ((presentablePath != actualPath || other.presentablePath != other.actualPath) && actualPath == other.actualPath) {
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