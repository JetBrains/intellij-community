// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.path.platform
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.collections.plusAssign

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
@ApiStatus.Internal
sealed class IjentNioPath protected constructor(
  internal val nioFs: IjentNioFileSystem,
  cachedAttributes: BasicFileAttributes?,
) : Path, BasicFileAttributesHolder2.Impl(cachedAttributes) {

  protected fun IjentNioPath.createRelativePath(segment: String): IjentNioPath {
    return RelativeIjentNioPath(listOf(segment), nioFs)
  }

  override fun getFileSystem(): IjentNioFileSystem = nioFs

  abstract override fun getRoot(): IjentNioPath?

  abstract override fun getParent(): IjentNioPath?

  abstract override fun subpath(beginIndex: Int, endIndex: Int): IjentNioPath

  abstract override fun normalize(): IjentNioPath

  final override fun resolve(segment: String): IjentNioPath {
    return super.resolve(segment) as IjentNioPath
  }

  abstract override fun resolve(path: Path): IjentNioPath
  abstract override fun relativize(path: Path): IjentNioPath

  abstract override fun toAbsolutePath(): IjentNioPath

  abstract override fun toRealPath(vararg options: LinkOption): IjentNioPath


  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    TODO("Not yet implemented")
  }

  override fun compareTo(other: Path): Int =
    toString().compareTo(other.toString())
}

internal class AbsoluteIjentNioPath(val eelPath: EelPath, nioFs: IjentNioFileSystem, cachedAttributes: BasicFileAttributes?) : IjentNioPath(nioFs, cachedAttributes) {
  override fun isAbsolute(): Boolean = true

  override fun getRoot(): IjentNioPath? {
    return eelPath.root.toNioPath(false)
  }

  override fun getFileName(): IjentNioPath? {
    return createRelativePath(eelPath.fileName)
  }

  private fun EelPath.toNioPath(preserveAttributes: Boolean): IjentNioPath =
    AbsoluteIjentNioPath(
      eelPath = this,  // Don't confuse with the parent "this".
      nioFs = nioFs,
      cachedAttributes = if (preserveAttributes) myCachedAttributes.get() else null,
    )

  override fun getParent(): IjentNioPath? =
    eelPath.parent?.toNioPath(false)

  override fun getNameCount(): Int =
    eelPath.nameCount

  override fun getName(index: Int): IjentNioPath =
    createRelativePath(eelPath.getName(index))

  override fun subpath(beginIndex: Int, endIndex: Int): IjentNioPath {
    return RelativeIjentNioPath(eelPath.parts.subList(beginIndex, endIndex), nioFs)
  }

  override fun startsWith(other: Path): Boolean {
    if (other !is IjentNioPath || other.fileSystem != this.fileSystem) {
      return false
    }
    when (other) {
      is AbsoluteIjentNioPath -> return eelPath.startsWith(other.eelPath)
      is RelativeIjentNioPath -> return false
    }
  }

  override fun endsWith(other: Path): Boolean {
    if (other !is IjentNioPath || other.fileSystem != this.fileSystem) {
      return false
    }
    when (other) {
      is AbsoluteIjentNioPath -> return eelPath == other.eelPath
      is RelativeIjentNioPath -> return eelPath.endsWith(other.segments)
    }
  }

  override fun normalize(): IjentNioPath = eelPath.normalize().toNioPath(true)

  override fun resolve(other: Path): IjentNioPath {
    if (other !is IjentNioPath) {
      throw ProviderMismatchException("Expected IJentNioPath, got $other")
    }
    return when (other) {
      is AbsoluteIjentNioPath -> other
      is RelativeIjentNioPath -> {
        val curatedSegments = other.segments.filter { it != "." && it != "" }
        other.segments.fold(eelPath) { acc, part -> acc.resolve(part) }.toNioPath(curatedSegments.isEmpty())
      }
    }
  }


  override fun relativize(other: Path): IjentNioPath {
    val otherEelPath = other.toEelPath()
    if (eelPath.root != otherEelPath.root) {
      throw EelPathException(other.root.toString(), "The other path has a different root")
    }

    var firstDifferenceIndex = 0
    while (firstDifferenceIndex < nameCount.coerceAtMost(other.nameCount)) {
      val different = getName(firstDifferenceIndex) != other.getName(firstDifferenceIndex)
      ++firstDifferenceIndex
      if (different) break
    }

    val result = mutableListOf<String>()
    repeat(nameCount - firstDifferenceIndex) {
      result += ".."
    }

    for (index in firstDifferenceIndex..<other.nameCount) {
      result += otherEelPath.getName(index)
    }

    return RelativeIjentNioPath(result, nioFs)
  }

  override fun toUri(): URI {
    val prefix = when (eelPath.platform) {
      is EelPlatform.Windows -> "/" + eelPath.root.toString().replace('\\', '/')
      is EelPlatform.Posix -> null
    }
    val allParts = listOfNotNull(prefix) + eelPath.parts
    return allParts.fold(nioFs.uri, URI::resolve)
  }

  override fun toAbsolutePath(): IjentNioPath {
    return this
  }

  override fun toRealPath(vararg options: LinkOption): IjentNioPath {
    return eelPath.normalize()
      .let { normalizedPath ->
        if (LinkOption.NOFOLLOW_LINKS in options)
          normalizedPath
        else
          fsBlocking {
            nioFs.ijentFs.canonicalize(normalizedPath)
          }.getOrThrowFileSystemException()
      }.toNioPath(true)
  }

  override fun toString(): String = eelPath.toString()

  /**
   * Commonly, instances of Path are not considered as equal if they actually represent the same path but come from different file systems.
   *
   * See [sun.nio.fs.UnixPath.equals] and [sun.nio.fs.WindowsPath.equals].
   */
  override fun equals(other: Any?): Boolean =
    other is AbsoluteIjentNioPath &&
    eelPath == other.eelPath &&
    nioFs == other.nioFs

  override fun hashCode(): Int =
    eelPath.hashCode() * 31 + nioFs.hashCode()
}

// cached attributes probably make no sense for relative paths
internal class RelativeIjentNioPath(val segments: List<String>, nioFs: IjentNioFileSystem) : IjentNioPath(nioFs, null) {
  override fun isAbsolute(): Boolean = false

  override fun getRoot(): IjentNioPath? {
    return null
  }

  override fun getFileName(): Path? {
    if (segments.size <= 1) {
      return this
    }
    return segments.lastOrNull()?.let { createRelativePath(it) }
  }

  override fun getNameCount(): Int {
    return segments.size
  }

  override fun getParent(): IjentNioPath? {
    if (segments.size <= 1) {
      return null
    }
    else {
      return RelativeIjentNioPath(segments.subList(0, segments.size - 1), nioFs)
    }
  }

  override fun getName(index: Int): IjentNioPath =
    createRelativePath(segments[index])

  override fun subpath(beginIndex: Int, endIndex: Int): IjentNioPath {
    return RelativeIjentNioPath(segments.subList(beginIndex, endIndex), nioFs)
  }

  override fun startsWith(other: Path): Boolean {
    if (other !is IjentNioPath || other.fileSystem != this.fileSystem) {
      return false
    }
    when (other) {
      is AbsoluteIjentNioPath -> return false
      is RelativeIjentNioPath -> return segments.size >= other.segments.size && segments.take(other.segments.size) == other.segments
    }
  }

  override fun endsWith(other: Path): Boolean {
    if (other !is IjentNioPath || other.fileSystem != this.fileSystem) {
      return false
    }
    when (other) {
      is AbsoluteIjentNioPath -> return false
      is RelativeIjentNioPath -> return segments.size >= other.segments.size && segments.takeLast(other.segments.size) == other.segments
    }
  }

  override fun normalize(): IjentNioPath {
    val result = mutableListOf<String>()
    for (part in segments) {
      when (part) {
        "." -> Unit

        ".." -> if (result.isNotEmpty() && result.last() != "..") {
          result.removeLast()
        }
        else {
          // we are either accessing a region of FS outside current relative path, or we are referencing an unknown parent
          // either way, this kind of normalization requires an access to the FS, which is out of the scope of `normalize`
          // for further normalization, `canonicalize` is required
          result += part
        }

        else ->
          result += part
      }
    }
    return RelativeIjentNioPath(result, nioFs)
  }


  override fun resolve(other: Path): IjentNioPath {
    if (other !is IjentNioPath) {
      throw ProviderMismatchException("Expected IJentNioPath, got $other")
    }
    return when (other) {
      is AbsoluteIjentNioPath -> other
      is RelativeIjentNioPath -> RelativeIjentNioPath(segments + other.segments, nioFs)
    }
  }

  override fun relativize(other: Path): IjentNioPath {
    if (other !is RelativeIjentNioPath || other.segments.size < segments.size) {
      throw IllegalArgumentException("Expected RelativeIjentNioPath, got $other")
    }
    return RelativeIjentNioPath(other.segments.subList(segments.size, other.segments.size), nioFs)
  }

  override fun toUri(): URI {
    throw InvalidPathException(toString(), "Can't create a URL from a relative path")
  }

  override fun toAbsolutePath(): IjentNioPath {
    // There are no benefits of building absolute paths from IJent's working directory, since it has no relation to projects, source
    // roots, etc. Let it fail early instead of generating a certainly incorrect path that will certainly lead to bugs sometime later.
    throw InvalidPathException(toString(), "Can't build an absolute path for $this")
  }

  override fun toRealPath(vararg options: LinkOption): IjentNioPath {
    throw InvalidPathException(toString(), "Can't find a real path for a relative path")
  }

  override fun toString(): String = segments.joinToString("/")

  /**
   * Commonly, instances of Path are not considered as equal if they actually represent the same path but come from different file systems.
   *
   * See [sun.nio.fs.UnixPath.equals] and [sun.nio.fs.WindowsPath.equals].
   */
  override fun equals(other: Any?): Boolean =
    other is RelativeIjentNioPath &&
    segments == other.segments &&
    nioFs == other.nioFs


  override fun hashCode(): Int =
    segments.hashCode() * 31 + nioFs.hashCode()
}