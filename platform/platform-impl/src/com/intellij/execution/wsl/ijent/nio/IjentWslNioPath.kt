// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import java.net.URI
import java.nio.file.*

class IjentWslNioPath(
  private val fileSystem: IjentWslNioFileSystem,
  val delegate: Path,
  cachedAttributes: IjentNioPosixFileAttributesWithDosAdapter?,
) : Path, BasicFileAttributesHolder2.Impl(cachedAttributes) {
  override fun getFileSystem(): IjentWslNioFileSystem = fileSystem

  override fun isAbsolute(): Boolean = delegate.isAbsolute

  override fun getRoot(): IjentWslNioPath? = delegate.root?.toIjentWslPath()

  override fun getFileName(): IjentWslNioPath? = delegate.fileName?.toIjentWslPath()

  override fun getParent(): IjentWslNioPath? = delegate.parent?.toIjentWslPath()

  override fun getNameCount(): Int = delegate.nameCount

  override fun getName(index: Int): IjentWslNioPath = delegate.getName(index).toIjentWslPath()

  override fun subpath(beginIndex: Int, endIndex: Int): IjentWslNioPath = delegate.subpath(beginIndex, endIndex).toIjentWslPath()

  override fun startsWith(other: Path): Boolean = delegate.startsWith(other.toOriginalPath())

  override fun endsWith(other: Path): Boolean = delegate.endsWith(other.toOriginalPath())

  override fun normalize(): IjentWslNioPath = delegate.normalize().toIjentWslPath()

  override fun resolve(other: Path): IjentWslNioPath = delegate.resolve(other.toOriginalPath()).toIjentWslPath()

  override fun relativize(other: Path): IjentWslNioPath = delegate.relativize(other.toOriginalPath()).toIjentWslPath()

  override fun toUri(): URI = delegate.toUri()

  override fun toAbsolutePath(): IjentWslNioPath = delegate.toAbsolutePath().toIjentWslPath()

  override fun toRealPath(vararg options: LinkOption): IjentWslNioPath {
    val ijentNioPath = fileSystem.provider().toIjentNioPath(this)
    val ijentNioRealPath = ijentNioPath.toRealPath(*options)
    val originalPath = fileSystem.provider().toOriginalPath(ijentNioRealPath)
    return originalPath.toIjentWslPath()
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>?>?, vararg modifiers: WatchEvent.Modifier?): WatchKey =
    delegate.register(watcher, events, *modifiers)

  override fun compareTo(other: Path): Int = delegate.compareTo(other.toOriginalPath())

  private fun Path.toIjentWslPath(): IjentWslNioPath =
    IjentWslNioPath(this@IjentWslNioPath.fileSystem, this, null)

  private fun Path.toOriginalPath(): Path =
    if (this is IjentWslNioPath) this.delegate
    else this

  override fun toString(): String = delegate.toString()

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is IjentWslNioPath -> false
    else -> fileSystem == other.fileSystem && delegate == other.delegate
  }

  override fun hashCode(): Int =
    fileSystem.hashCode() + 31 * delegate.hashCode()
}