// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
import java.net.URI
import java.nio.file.*

/**
 * Such paths are supposed to be created via the corresponding nio FileSystem. [Path.of] does NOT return instances of this class.
 *
 * How to get a path:
 * ```kotlin
 * val ijent: IjentApi = getIjentFromSomewhere()
 * val path: Path = ijent.fs.asNioFileSystem().getPath("/usr/bin/cowsay")
 * ```
 */
class IjentNioPath internal constructor(
  val ijentPath: IjentPath,
  internal val nioFs: IjentNioFileSystem,
) : Path {
  private val isWindows
    get() = when (nioFs.ijentFs) {
      is IjentFileSystemPosixApi -> false
      is IjentFileSystemWindowsApi -> true
    }

  override fun getFileSystem(): IjentNioFileSystem = nioFs

  override fun isAbsolute(): Boolean =
    when (ijentPath) {
      is IjentPath.Absolute -> true
      is IjentPath.Relative -> false
    }

  override fun getRoot(): IjentNioPath? =
    when (ijentPath) {
      is IjentPath.Absolute -> ijentPath.root.toNioPath()
      is IjentPath.Relative -> null
    }

  override fun getFileName(): IjentNioPath? =
    IjentPath.Relative
      .parse(ijentPath.fileName)
      .getOrThrow()
      .toNioPath()
      .takeIf { it.nameCount > 0 }

  override fun getParent(): IjentNioPath? =
    ijentPath.parent?.toNioPath()

  override fun getNameCount(): Int =
    ijentPath.nameCount

  override fun getName(index: Int): IjentNioPath =
    ijentPath.getName(index).toNioPath()

  override fun subpath(beginIndex: Int, endIndex: Int): IjentNioPath =
    TODO()

  override fun startsWith(other: Path): Boolean {
    val otherIjentPath = other.toIjentPath(isWindows)
    return when (ijentPath) {
      is IjentPath.Absolute -> when (otherIjentPath) {
        is IjentPath.Absolute -> ijentPath.startsWith(otherIjentPath)
        is IjentPath.Relative -> false
      }
      is IjentPath.Relative -> when (otherIjentPath) {
        is IjentPath.Absolute -> false
        is IjentPath.Relative -> ijentPath.startsWith(otherIjentPath)
      }
    }
  }

  override fun endsWith(other: Path): Boolean =
    when (val otherIjentPath = other.toIjentPath(isWindows)) {
      is IjentPath.Absolute -> ijentPath == otherIjentPath
      is IjentPath.Relative -> ijentPath.endsWith(otherIjentPath)
    }

  override fun normalize(): IjentNioPath =
    when (ijentPath) {
      is IjentPath.Absolute -> ijentPath.normalize().getOrThrow().toNioPath()
      is IjentPath.Relative -> ijentPath.normalize().toNioPath()
    }

  override fun resolve(other: Path): IjentNioPath =
    when (val otherIjentPath = other.toIjentPath(isWindows)) {
      is IjentPath.Absolute -> otherIjentPath.toNioPath()  // TODO is it the desired behaviour?
      is IjentPath.Relative -> ijentPath.resolve(otherIjentPath).getOrThrow().toNioPath()
    }

  override fun relativize(other: Path): IjentNioPath =
    when (val otherIjentPath = other.toIjentPath(isWindows)) {
      is IjentPath.Absolute -> when (ijentPath) {
        is IjentPath.Absolute -> ijentPath.relativize(otherIjentPath).getOrThrow().toNioPath()
        is IjentPath.Relative -> throw InvalidPathException("$this.relativize($other)",
                                                            "Can't relativize these paths")
      }
      is IjentPath.Relative -> throw InvalidPathException("$this.relativize($other)",
                                                          "Can't relativize these paths")
    }

  override fun toUri(): URI =
    when (ijentPath) {
      is IjentPath.Absolute ->
        nioFs.uri.resolve(ijentPath.toString())

      is IjentPath.Relative ->
        throw InvalidPathException(toString(), "Can't create a URL from a relative path") // TODO Really no way?
    }

  override fun toAbsolutePath(): IjentNioPath =
    if (isAbsolute)
      this
    else {
      // There are no benefits of building absolute paths from IJent's working directory, since it has no relation to projects, source
      // roots, etc. Let it fail early instead of generating a certainly incorrect path that will certainly lead to bugs sometime later.
      throw InvalidPathException(toString(), "Can't build an absolute path for $this")
    }

  override fun toRealPath(vararg options: LinkOption): IjentNioPath =
    when (ijentPath) {
      is IjentPath.Absolute ->
        ijentPath.normalize().getOrThrow()
          .let { normalizedPath ->
            if (LinkOption.NOFOLLOW_LINKS in options)
              normalizedPath
            else
              fsBlocking {
                nioFs.ijentFs.canonicalize(normalizedPath)
              }
                .getOrThrowFileSystemException()
          }
          .toNioPath()

      is IjentPath.Relative ->
        throw InvalidPathException(toString(), "Can't find a real path for a relative path")
    }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    TODO("Not yet implemented")
  }

  override fun compareTo(other: Path): Int =
    toString().compareTo(other.toString())

  override fun toString(): String = "$ijentPath"

  private fun IjentPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      ijentPath = this,  // Don't confuse with the parent "this".
      nioFs = nioFs,
    )

  /**
   * Commonly, instances of Path are not considered as equal if they actually represent the same path but come from different file systems.
   *
   * See [sun.nio.fs.UnixPath.equals] and [sun.nio.fs.WindowsPath#equals].
   */
  override fun equals(other: Any?): Boolean =
    other is IjentNioPath &&
    ijentPath == other.ijentPath &&
    nioFs == other.nioFs

  override fun hashCode(): Int =
    ijentPath.hashCode() * 31 + nioFs.hashCode()
}