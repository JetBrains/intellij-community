// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath.OS

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
    fun parse(raw: String, os: OS?): EelPath {
      return ArrayListEelAbsolutePath.parseOrNull(raw, os) ?: throw EelPathException(raw, "Invalid absolute path")
    }

    @Throws(EelPathException::class)
    @JvmStatic
    fun build(parts: List<String>, os: OS?): EelPath {
      return ArrayListEelAbsolutePath.build(parts, os)
    }
  }

  val fileName: String

  val root: EelPath

  /**
   * Returns the number of elements in the path.
   *
   * ```kotlin
   * IjentRelativePath.parse("", false).nameCount == 1
   * IjentRelativePath.parse("a", false).nameCount == 1
   * IjentRelativePath.parse("a/b/c", false).nameCount == 3
   * ```
   *
   * ```kotlin
   * IjentAbsolutePath.parse("C:\\", isWindows = true).nameCount == 0
   * IjentAbsolutePath.parse("C:\\Users", isWindows = true).nameCount == 1
   * IjentAbsolutePath.parse("C:\\Users\\username", isWindows = true).nameCount == 2
   * ```
   *
   * See [java.nio.file.Path.getNameCount]
   */
  val nameCount: Int

  /**
   * Returns a part of the path.
   *
   * This method tries to behave like [java.nio.file.Path.getName].
   *
   * ```kotlin
   * IjentRelativePath.parse("", false).getName(0) == ""
   * IjentRelativePath.parse("a", false).getName(0) == "a"
   * IjentRelativePath.parse("a/b/c", false).getName(1) == "b"
   * ```
   *
   * ```kotlin
   * IjentAbsolutePath.parse("C:\\Users\\username", isWindows = true).getName(0) == "Users"
   * ```
   */
  fun getName(index: Int): String

  /**
   * Return the parent path if it exists.
   *
   * ```kotlin
   * IjentRelativePath.parse("", false).parent == null
   * IjentRelativePath.parse("a", false).parent == null
   * IjentRelativePath.parse("a/b/c", false).parent == IjentRelativePath.parse("a/b", false)
   * ```
   */
  val parent: EelPath?

  /**
   * ```kotlin
   * IjentRelativePath.parse("", false).endsWith(IjentRelativePath.parse("", false)) == true
   * IjentRelativePath.parse("a", false).endsWith(IjentRelativePath.parse("", false)) == true
   * IjentRelativePath.parse("a/b/c", false).endsWith(IjentRelativePath.parse("b/c", false)) == true
   * IjentRelativePath.parse("a/b/cde", false).endsWith(IjentRelativePath.parse("b/c", false)) == false
   * ```
   */
  fun endsWith(other: EelPath): Boolean

  /**
   * Concatenates two paths.
   *
   * ```kotlin
   * IjentRelativePath.parse("abc/..", false).resolve(IjentRelativePath.parse("def", false)) == IjentRelativePath.parse("abc/../def", false)
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
   * IjentRelativePath.parse("abc/./../def", false).normalize() == IjentRelativePath.parse("def", false)
   * IjentRelativePath.parse("abc/../x/../../../def", false).normalize() == IjentRelativePath.parse("../../def", false)
   * ```
   */
  fun normalize(): EelPath


  /** See [java.nio.file.Path.startsWith] */
  fun startsWith(other: EelPath): Boolean

  /**
   * ```kotlin
   * IjentRelativePath.parse("", false).getChild("abc") == Ok(IjentRelativePath.parse("abc", false))
   * IjentRelativePath.parse("abc", false).getChild("..") == Ok(IjentRelativePath.parse("abc/..", false))
   * IjentRelativePath.parse("abc", false).getChild("x/y/z") == Err(...)
   * IjentRelativePath.parse("abc", false).getChild("x\y\z") == Ok(IjentRelativePath.parse("abc/x\y\z", false))
   * IjentRelativePath.parse("abc", true).getChild("x\y\z") == Err(...)
   * IjentRelativePath.parse("abc", false).getChild("") == Err(...)
   * ```
   */
  @Throws(EelPathException::class)
  fun getChild(name: String): EelPath

  fun parts(): List<String>

  fun toDebugString(): String

  override fun toString(): String

  enum class OS {
    WINDOWS, UNIX
  }
}

operator fun EelPath.div(part: String): EelPath = resolve(part)

val OS.pathSeparator: String
  get() = when (this) {
    OS.UNIX -> ":"
    OS.WINDOWS -> ";"
  }

val EelPlatform.pathOs: OS
  get() = when (this) {
    is EelPlatform.Posix -> OS.UNIX
    is EelPlatform.Windows -> OS.WINDOWS
  }


interface EelPathError {
  val raw: String
  val reason: String
}

class EelPathException(override val raw: String, override val reason: String) : RuntimeException("`$raw`: $reason"), EelPathError
