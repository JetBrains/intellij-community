// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import java.net.URI
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

class IjentWslNioPath(private val fileSystem: IjentWslNioFileSystem, val delegate: Path) : Path {
  override fun getFileSystem(): IjentWslNioFileSystem = fileSystem

  override fun isAbsolute(): Boolean = delegate.isAbsolute

  override fun getRoot(): Path? = delegate.root?.toIjentWslPath()

  override fun getFileName(): Path? = delegate.fileName?.toIjentWslPath()

  override fun getParent(): Path? = delegate.parent?.toIjentWslPath()

  override fun getNameCount(): Int = delegate.nameCount

  override fun getName(index: Int): Path = delegate.getName(index).toIjentWslPath()

  override fun subpath(beginIndex: Int, endIndex: Int): Path = delegate.subpath(beginIndex, endIndex).toIjentWslPath()

  override fun startsWith(other: Path): Boolean = delegate.startsWith(other.toOriginalPath())

  override fun endsWith(other: Path): Boolean = delegate.endsWith(other.toOriginalPath())

  override fun normalize(): Path = delegate.normalize().toIjentWslPath()

  override fun resolve(other: Path): Path = delegate.resolve(other.toOriginalPath()).toIjentWslPath()

  override fun relativize(other: Path): Path = delegate.relativize(other.toOriginalPath()).toIjentWslPath()

  override fun toUri(): URI = delegate.toUri()

  override fun toAbsolutePath(): Path = delegate.toAbsolutePath().toIjentWslPath()

  override fun toRealPath(vararg options: LinkOption?): Path = delegate.toRealPath(*options).toIjentWslPath()

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>?>?, vararg modifiers: WatchEvent.Modifier?): WatchKey =
    delegate.register(watcher, events, *modifiers)

  override fun compareTo(other: Path): Int = delegate.compareTo(other.toOriginalPath())

  private fun Path.toIjentWslPath(): IjentWslNioPath =
    IjentWslNioPath(this@IjentWslNioPath.fileSystem, this)

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