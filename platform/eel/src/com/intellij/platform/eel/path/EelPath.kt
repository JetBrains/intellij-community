// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath.Absolute.OS
import java.nio.file.InvalidPathException

sealed interface EelPathResult<P : EelPath> {
  interface Ok<P : EelPath> : EelPathResult<P> {
    val path: P
  }
  interface Err<P : EelPath> : EelPathResult<P> {
    val raw: String
    val reason: String
  }
}

/**
 * This interface deliberately mimics API of [java.nio.file.Path].
 *
 * On Unix, the first element of an absolute path is always '/'.
 * On Windows, the root may contain several '\'.
 *
 * It consists of all methods of nio.Path which don't require any I/O.
 */
sealed interface EelPath {
  companion object {
    @JvmStatic
    fun parse(raw: String, os: OS?): EelPathResult<out EelPath> =
      when (val absoluteResult = Absolute.parse(raw, os)) {
        is EelPathResult.Ok -> absoluteResult
        is EelPathResult.Err -> Relative.parse(raw)
      }
  }

  val fileName: String

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
  fun getName(index: Int): Relative

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
  fun endsWith(other: Relative): Boolean

  /**
   * Concatenates two paths.
   *
   * ```kotlin
   * IjentRelativePath.parse("abc/..", false).resolve(IjentRelativePath.parse("def", false)) == IjentRelativePath.parse("abc/../def", false)
   * ```
   *
   * // TODO Wouldn't it be better to return different types for relative and absolute paths?
   * It should fail in cases like Absolute("/").resolve(Relative("..")).
   */
  fun resolve(other: Relative): EelPathResult<out EelPath>

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
  fun getChild(name: String): EelPathResult<out EelPath>

  override fun toString(): String

  interface Relative : EelPath, Comparable<Relative> {
    companion object {
      @JvmStatic
      fun parse(raw: String): EelPathResult<out Relative> =
        ArrayListEelRelativePath.parse(raw)

      /**
       * The parts of the path must not contain / or \.
       */
      @JvmStatic
      fun build(vararg parts: String): EelPathResult<out Relative> =
        build(listOf(*parts))

      /**
       * The parts of the path must not contain / or \.
       */
      @JvmStatic
      fun build(parts: List<String>): EelPathResult<out Relative> =
        ArrayListEelRelativePath.build(parts)

      @JvmField
      val EMPTY: Relative = ArrayListEelRelativePath.EMPTY
    }

    override val parent: Relative?

    /** See [java.nio.file.Path.startsWith] */
    fun startsWith(other: Relative): Boolean

    override fun resolve(other: Relative): EelPathResult<out Relative>

    override fun getChild(name: String): EelPathResult<out Relative>

    override fun compareTo(other: Relative): Int

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
    fun normalize(): Relative
  }

  /**
   * This interface deliberately mimics API of [java.nio.file.Path].
   *
   * On Unix, the first element of an absolute path is always '/'.
   * On Windows, the root may contain several '\'.
   *
   * It consists of all methods of nio.Path which don't require any I/O.
   */
  interface Absolute : EelPath, Comparable<Absolute> {
    companion object {
      @JvmStatic
      fun parse(raw: String, os: OS?): EelPathResult<out Absolute> =
        ArrayListEelAbsolutePath.parse(raw, os)

      @JvmStatic
      fun build(vararg parts: String): EelPathResult<out Absolute> =
        build(listOf(*parts), null)

      @JvmStatic
      fun build(parts: List<String>, os: OS?): EelPathResult<out Absolute> =
        ArrayListEelAbsolutePath.build(parts, os)
    }

    enum class OS {
      WINDOWS, UNIX
    }

    val os: OS

    /** See [java.nio.file.Path.getRoot] */
    val root: Absolute

    /** See [java.nio.file.Path.getParent] */
    override val parent: Absolute?

    fun startsWith(other: Absolute): Boolean

    /** See [java.nio.file.Path.normalize] */
    fun normalize(): EelPathResult<out Absolute>

    /** See [java.nio.file.Path.resolve] */
    override fun resolve(other: Relative): EelPathResult<out Absolute>

    /**
     * See [java.nio.file.Path.relativize].
     *
     * ```kotlin
     * IjentPathAbsolute.parse("C:\\foo\\bar\\baz", isWindows = true).relativize(IjentPathAbsolute.parse("C:\\foo\\oops", isWindows = true))
     *   == IjentPathAbsolute.parse("..\..\oops", isWindows = true)
     * ```
     */
    fun relativize(other: Absolute): EelPathResult<out Relative>

    override fun getChild(name: String): EelPathResult<out Absolute>

    fun scan(): Sequence<Absolute>

    /** See [java.nio.file.Path.toString] */
    override fun toString(): String

    /** TODO Describe the difference with [toString] */
    fun toDebugString(): String
  }
}

@Throws(InvalidPathException::class)
fun <P : EelPath> EelPathResult<P>.getOrThrow(): P =
  when (this) {
    is EelPathResult.Ok -> path
    is EelPathResult.Err -> throw InvalidPathException(raw, reason)
  }

val EelPlatform.pathOs: OS
  get() = when (this) {
    is EelPlatform.Posix -> OS.UNIX
    is EelPlatform.Windows -> OS.WINDOWS
  }