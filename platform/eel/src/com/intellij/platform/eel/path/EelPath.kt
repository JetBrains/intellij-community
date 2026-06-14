// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val EelPath.platform: EelOsFamily get() = descriptor.osFamily

/**
 * An interface for **absolute** paths on some environment.
 *
 * [EelPath] is immutable. Its instances may be interned, so you should make no assumptions about the identity of the path.
 *
 * On Unix, the first element of an absolute path is always '/'.
 * On Windows, the root may contain several '\'.
 *
 * All operations listed here do not require I/O.
 *
 * In the examples below, `descriptor` is a POSIX [EelDescriptor] and `windowsDescriptor` is a Windows one.
 */
@ApiStatus.Experimental
sealed interface EelPath {
  companion object {
    @Throws(EelPathException::class)
    @JvmStatic
    fun parse(raw: String, descriptor: EelDescriptor): EelPath {
      return ArrayListEelAbsolutePath.parseOrNull(raw, descriptor) ?: throw EelPathException(raw, "Not a valid absolute path")
    }

    @Throws(EelPathException::class)
    @JvmStatic
    @ApiStatus.Internal
    fun build(parts: List<String>, descriptor: EelDescriptor): EelPath {
      return ArrayListEelAbsolutePath.build(parts, descriptor)
    }
  }

  /**
   * The identifier of an environment where this path belongs to.
   */
  val descriptor: EelDescriptor

  /**
   * Returns last part of a path.
   *
   * ```kotlin
   * EelPath.parse("C:\\a\\b\\c", windowsDescriptor).fileName == "c"
   * EelPath.parse("C:\\", windowsDescriptor).fileName == "C:\\"  // a root has no component, so its own name is returned
   * ```
   */
  val fileName: String

  /**
   * Returns a path that corresponds to the root.
   *
   * ```kotlin
   * EelPath.parse("C:\\a\\b\\c", windowsDescriptor).root == EelPath.parse("C:\\", windowsDescriptor)
   * EelPath.parse("/a/b/c", descriptor).root == EelPath.parse("/", descriptor)
   * ```
   */
  val root: EelPath

  /**
   * Returns parts of a path composed as a list.
   *
   * ```kotlin
   * EelPath.parse("/abc/def/ghi", descriptor).parts == listOf("abc", "def", "ghi")
   * EelPath.parse("C:\\abc\\def\\ghi", windowsDescriptor).parts == listOf("abc", "def", "ghi")
   * ```
   */
  val parts: List<String>

  /**
   * Returns the number of elements in the path.
   *
   * ```kotlin
   * EelPath.parse("C:\\", windowsDescriptor).nameCount == 0
   * EelPath.parse("C:\\Users\\username", windowsDescriptor).nameCount == 2
   * ```
   */
  val nameCount: Int

  /**
   * Returns a part of the path.
   *
   * ```kotlin
   * EelPath.parse("C:\\Users\\username", windowsDescriptor).getName(0) == "Users"
   * ```
   */
  fun getName(index: Int): String

  /**
   * Return the parent path if it exists.
   *
   * ```kotlin
   * EelPath.parse("/", descriptor).parent == null
   * EelPath.parse("/a", descriptor).parent == EelPath.parse("/", descriptor)
   * EelPath.parse("/a/b/c", descriptor).parent == EelPath.parse("/a/b", descriptor)
   * ```
   */
  val parent: EelPath?

  /**
   * Concatenates two paths.
   *
   * ```kotlin
   * EelPath.parse("/abc/..", descriptor).resolve("def") == EelPath.parse("/abc/../def", descriptor)
   * ```
   */
  @Throws(EelPathException::class)
  fun resolve(other: String): EelPath

  /**
   * Resolves special path elements like `.` and `..` whenever it is possible.
   *
   * Does not perform any access to the file system.
   *
   * ```kotlin
   * EelPath.parse("/abc/./../def", descriptor).normalize() == EelPath.parse("/def", descriptor)
   * ```
   */
  fun normalize(): EelPath

  /** See [java.nio.file.Path.startsWith] */
  fun startsWith(other: EelPath): Boolean

  /** See [java.nio.file.Path.endsWith] */
  fun endsWith(suffix: List<String>): Boolean

  /**
   * Appends a single path component. Unlike [resolve], it does not split [name] on separators — a separator inside [name] is rejected.
   *
   * ```kotlin
   * EelPath.parse("/abc", descriptor).getChild("..") == EelPath.parse("/abc/..", descriptor)  // ".." is a literal name, not resolved
   * EelPath.parse("/abc", descriptor).getChild("x/y/z")  // throws: '/' is not allowed inside a name
   * EelPath.parse("/abc", descriptor).getChild("")       // throws: empty name
   * EelPath.parse("C:\\abc", windowsDescriptor).getChild("x\\y\\z")  // throws: '\' is not allowed inside a Windows name
   * ```
   */
  @Throws(EelPathException::class)
  fun getChild(name: String): EelPath

  fun toDebugString(): String

  /**
   * @return path in the particular eel, i.e.: `/foo` or `c:\bar`
   */
  override fun toString(): String

  /** @see [kotlin.io.path.invariantSeparatorsPathString] */
  val invariantSeparatorsPathString: String

  @Deprecated("Use EelPlatform instead, will be removed soon")
  enum class OS {
    WINDOWS, UNIX
  }
}

@ApiStatus.Internal
operator fun EelPath.div(part: String): EelPath = resolve(part)

@ApiStatus.Experimental
class EelPathException(val raw: String, val reason: String) : RuntimeException("`$raw`: $reason")
