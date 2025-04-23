// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform

val EelPath.platform: EelPlatform get() = descriptor.platform

/**
 * An interface for **absolute** paths on some environment.
 *
 * [EelPath] is immutable. Its instances may be interned, so you should make no assumptions about the identity of the path.
 *
 * On Unix, the first element of an absolute path is always '/'.
 * On Windows, the root may contain several '\'.
 *
 * All operations listed here do not require I/O.
 */
sealed interface EelPath {
  companion object {
    @Throws(EelPathException::class)
    @JvmStatic
    fun parse(raw: String, descriptor: EelDescriptor): EelPath {
      return ArrayListEelAbsolutePath.parseOrNull(raw, descriptor) ?: throw EelPathException(raw, "Not a valid absolute path")
    }

    @Throws(EelPathException::class)
    @JvmStatic
    fun build(parts: List<String>, descriptor: EelDescriptor): EelPath {
      return ArrayListEelAbsolutePath.build(parts, descriptor)
    }
  }

  /**
   * The identificator of an environment where this path belongs to.
   */
  val descriptor: EelDescriptor


  /**
   * Returns last part of a path.
   *
   * ```kotlin
   * EelPath.parse("C:\\a\\b\\c").fileName == "c"
   * EelPath.parse("C:\\").fileName == ""
   * ```
   */
  val fileName: String


  /**
   * Returns a path that corresponds to the root.
   *
   * ```kotlin
   * EelPath.parse("C:\\a\\b\\c").root == EelPath.parse("C:\\")
   * EelPath.parse("/a/b/c").root == EelPath.parse("/")
   * ```
   */
  val root: EelPath


  /**
   * Returns parts of a path composed as a list.
   *
   * ```kotlin
   * EelPath.parse("/abc/def/ghi", OS.UNIX).parts == listOf("abc", "def", "ghi")
   * EelPath.parse("C:\\abc\\def\\ghi", OS.WINDOWS).parts == listOf("abc", "def", "ghi")
   * ```
   */
  val parts: List<String>


  /**
   * Returns the number of elements in the path.
   *
   * ```kotlin
   * EelPath.parse("C:\\").nameCount == 0
   * EelPath.parse("C:\\Users\\username").nameCount == 2
   * ```
   */
  val nameCount: Int


  /**
   * Returns a part of the path.
   *
   * ```kotlin
   * EelPath.parse("C:\\Users\\username").getName(0) == "Users"
   * ```
   */
  fun getName(index: Int): String


  /**
   * Return the parent path if it exists.
   *
   * ```kotlin
   * EelPath.parse("/a").parent == null
   * EelPath.parse("/a/b/c").parent == EelPath.parse("a/b")
   * ```
   */
  val parent: EelPath?


  /**
   * Concatenates two paths.
   *
   * ```kotlin
   * EelPath.parse("/abc/..").resolve("def") == EelPath.parse("/abc/../def")
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
   * EelPath.parse("/abc/./../def").normalize() == EelPath.parse("/def")
   * ```
   */
  fun normalize(): EelPath


  /** See [java.nio.file.Path.startsWith] */
  fun startsWith(other: EelPath): Boolean

  /** See [java.nio.file.Path.endsWith] */
  fun endsWith(suffix: List<String>): Boolean

  /**
   * ```kotlin
   * EelPath.parse("/abc", OS.UNIX).getChild("..") == EelPath.parse("abc/..", false)
   * EelPath.parse("/abc", OS.UNIX).getChild("x/y/z") will throw
   * EelPath.parse("/abc", OS.UNIX).getChild("x\y\z") == EelPath.parse("/abc/x\y\z")
   * EelPath.parse("C:\\abc", OS.WINDOWS).getChild("x\y\z") will throw
   * EelPath.parse("abc", false).getChild("") will throw
   * ```
   */
  @Throws(EelPathException::class)
  fun getChild(name: String): EelPath

  fun toDebugString(): String

  override fun toString(): String

  @Deprecated("Use EelPlatform instead, will be removed soon")
  enum class OS {
    WINDOWS, UNIX
  }
}

operator fun EelPath.div(part: String): EelPath = resolve(part)

class EelPathException(val raw: String, val reason: String) : RuntimeException("`$raw`: $reason")
