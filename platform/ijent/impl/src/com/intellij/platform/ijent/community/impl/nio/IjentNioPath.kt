// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.eel.path.EelPath
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Such paths are supposed to be created via the corresponding nio FileSystem. [Path.of] does NOT return instances of this class.
 *
 * How to get a path using [IjentNioFileSystemProvider]:
 * ```kotlin
 * val ijent: IjentApi = getIjentFromSomewhere()
 * val fs = IjentNioFileSystemProvider.getInstance()
 *   .getFileSystem(URI("ijent://some-id-that-you-should-know"))
 *   .getPath("/usr/bin/cowsay")
 * ```
 */
class IjentNioPath internal constructor(
  val eelPath: EelPath,
  internal val nioFs: IjentNioFileSystem,
  cachedAttributes: BasicFileAttributes?,
) : Path, BasicFileAttributesHolder2.Impl(cachedAttributes) {
  override fun getFileSystem(): IjentNioFileSystem = nioFs

  override fun isAbsolute(): Boolean =
    when (eelPath) {
      is EelPath.Absolute -> true
      is EelPath.Relative -> false
    }

  override fun getRoot(): IjentNioPath? =
    when (eelPath) {
      is EelPath.Absolute -> eelPath.root.toNioPath()
      is EelPath.Relative -> null
    }

  override fun getFileName(): IjentNioPath? =
    EelPath.Relative
      .parseE(eelPath.fileName)
      .toNioPath()
      .takeIf { it.nameCount > 0 }

  override fun getParent(): IjentNioPath? =
    eelPath.parent?.toNioPath()

  override fun getNameCount(): Int =
    eelPath.nameCount

  override fun getName(index: Int): IjentNioPath =
    eelPath.getName(index).toNioPath()

  override fun subpath(beginIndex: Int, endIndex: Int): IjentNioPath =
    TODO()

  override fun startsWith(other: Path): Boolean {
    val otherEelPath = try {
      other.toEelPath()
    }
    catch (_: InvalidPathException) {
      return false
    }

    return when (eelPath) {
      is EelPath.Absolute -> when (otherEelPath) {
        is EelPath.Absolute -> eelPath.startsWith(otherEelPath)
        is EelPath.Relative -> false
      }
      is EelPath.Relative -> when (otherEelPath) {
        is EelPath.Absolute -> false
        is EelPath.Relative -> eelPath.startsWith(otherEelPath)
      }
    }
  }

  override fun endsWith(other: Path): Boolean =
    when (val otherIjentPath = other.toEelPath()) {
      is EelPath.Absolute -> eelPath == otherIjentPath
      is EelPath.Relative -> eelPath.endsWith(otherIjentPath)
    }

  override fun normalize(): IjentNioPath =
    when (eelPath) {
      is EelPath.Absolute -> eelPath.normalizeE().toNioPath()
      is EelPath.Relative -> eelPath.normalize().toNioPath()
    }

  override fun resolve(other: Path): IjentNioPath =
    when (val otherIjentPath = other.toEelPath()) {
      is EelPath.Absolute -> otherIjentPath.toNioPath()  // TODO is it the desired behaviour?
      is EelPath.Relative -> eelPath.resolveE(otherIjentPath).toNioPath()
    }

  override fun relativize(other: Path): IjentNioPath =
    when (val otherIjentPath = other.toEelPath()) {
      is EelPath.Absolute -> when (eelPath) {
        is EelPath.Absolute -> eelPath.relativizeE(otherIjentPath).toNioPath()
        is EelPath.Relative -> throw InvalidPathException("$this.relativize($other)",
                                                          "Can't relativize these paths")
      }
      is EelPath.Relative -> throw InvalidPathException("$this.relativize($other)",
                                                        "Can't relativize these paths")
    }

  override fun toUri(): URI =
    when (eelPath) {
      is EelPath.Absolute ->
        nioFs.uri.resolve(eelPath.toString())

      is EelPath.Relative ->
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
    when (eelPath) {
      is EelPath.Absolute ->
        eelPath.normalizeE()
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

      is EelPath.Relative ->
        throw InvalidPathException(toString(), "Can't find a real path for a relative path")
    }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    TODO("Not yet implemented")
  }

  override fun compareTo(other: Path): Int =
    toString().compareTo(other.toString())

  override fun toString(): String = "$eelPath"

  private fun EelPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      eelPath = this,  // Don't confuse with the parent "this".
      nioFs = nioFs,
      cachedAttributes = null,
    )

  /**
   * Commonly, instances of Path are not considered as equal if they actually represent the same path but come from different file systems.
   *
   * See [sun.nio.fs.UnixPath.equals] and [sun.nio.fs.WindowsPath#equals].
   */
  override fun equals(other: Any?): Boolean =
    other is IjentNioPath &&
    eelPath == other.eelPath &&
    nioFs == other.nioFs

  override fun hashCode(): Int =
    eelPath.hashCode() * 31 + nioFs.hashCode()
}