// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.fs.IjentPath
import org.jetbrains.annotations.ApiStatus
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
@ApiStatus.Experimental
class IjentNioPath internal constructor(
  val ijentPath: IjentPath,
  private val nioFs: IjentNioFileSystem,
) : Path {
  override fun getFileSystem(): FileSystem = nioFs

  override fun isAbsolute(): Boolean =
    ijentPath.isAbsolute

  override fun getRoot(): Path? =
    ijentPath.root?.toNioPath()

  override fun getFileName(): Path? =
    ijentPath.fileName?.toNioPath()

  override fun getParent(): Path? =
    ijentPath.parent?.toNioPath()

  override fun getNameCount(): Int =
    ijentPath.nameCount

  override fun getName(index: Int): Path =
    ijentPath.getName(index).toNioPath()

  override fun subpath(beginIndex: Int, endIndex: Int): Path =
    ijentPath.subpath(beginIndex, endIndex).toNioPath()

  override fun startsWith(other: Path): Boolean =
    other is IjentNioPath && ijentPath.startsWith(other.ijentPath)

  override fun endsWith(other: Path): Boolean =
    other is IjentNioPath && ijentPath.endsWith(other.ijentPath)

  override fun normalize(): Path =
    ijentPath.normalize().toNioPath()

  override fun resolve(other: Path): Path {
    val result =
      if (other is IjentNioPath)
        ijentPath.resolve(other.ijentPath)
      else {
        // TODO Is this branch really needed? Wouldn't it better to throw IllegalArgumentException?
        val start =
          if (other.isAbsolute)
            ijentPath.root ?: throw InvalidPathException(this.toString(), "No root path")
          else
            ijentPath
        other.fold(start) { p, o -> p.resolve(o.toString()) }
      }
    return result.toNioPath()
  }

  override fun relativize(other: Path): Path {
    require(other is IjentNioPath)
    return ijentPath.relativize(other.ijentPath).toNioPath()
  }

  // TODO Hadn't it better propagate the method to IjentRemotePath?
  override fun toUri(): URI =
    URI(
      "ijent",
      ijentPath.ijentId.id,
      ijentPath.joinToString("/"),
      null,
    )

  override fun toAbsolutePath(): Path =
    if (isAbsolute)
      this
    else {
      // There are no benefits of building absolute paths from IJent's working directory, since it has no relation to projects, source
      // roots, etc. Let it fail early instead of generating a certainly incorrect path that will certainly lead to bugs sometime later.
      throw UnsupportedOperationException("Can't build an absolute path for $this")
    }

  override fun toRealPath(vararg options: LinkOption): Path =
    if (LinkOption.NOFOLLOW_LINKS in options)
      ijentPath.normalize().toNioPath()
    else
      nioFs.fsBlocking {
        nioFs.ijentFsApi.canonicalize(ijentPath).getOrThrow(this@IjentNioPath.toString()).toNioPath()
      }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    TODO("Not yet implemented")
  }

  override fun compareTo(other: Path): Int =
    if (other is IjentNioPath)
      ijentPath.compareTo(other.ijentPath)
    else {
      asSequence()
        .zip(other.asSequence())
        .map { (l, r) -> l.compareTo(r) }
        .plus(sequenceOf(nameCount - other.nameCount))
        .find { it != 0 }
      ?: 0
    }

  override fun toString(): String = "$ijentPath"

  private fun IjentPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      ijentPath = this,  // Don't confuse with the parent "this".
      nioFs = nioFs,
    )
}