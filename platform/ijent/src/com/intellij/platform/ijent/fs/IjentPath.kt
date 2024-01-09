// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.fs.IjentPathResult.Ok
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface IjentPathResult<P : IjentPath> {
  data class Ok<P : IjentPath>(val path: P) : IjentPathResult<P>
  data class Err<P : IjentPath>(val raw: String, val reason: String) : IjentPathResult<P>
}

/**
 * This interface deliberately mimics API of [java.nio.file.Path].
 *
 * On Unix, the first element of an absolute path is always '/'.
 * On Windows, the root may contain several '\'.
 *
 * It consists of all methods of nio.Path which don't require any I/O.
 */
@ApiStatus.Experimental
sealed interface IjentPath {
  companion object {
    @JvmStatic
    fun parse(raw: String, os: Absolute.OS?): IjentPathResult<out IjentPath> =
      when (val absoluteResult = Absolute.parse(raw, os)) {
        is Ok -> absoluteResult
        is IjentPathResult.Err -> Relative.parse(raw)
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
  val parent: IjentPath?

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
  fun resolve(other: Relative): IjentPathResult<out IjentPath>

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
  fun getChild(name: String): IjentPathResult<out IjentPath>

  override fun toString(): String

  interface Relative : IjentPath, Comparable<Relative> {
    companion object {
      @JvmStatic
      fun parse(raw: String): IjentPathResult<out Relative> =
        ArrayListIjentRelativePath.parse(raw)

      /**
       * The parts of the path must not contain / or \.
       */
      @JvmStatic
      fun build(vararg parts: String): IjentPathResult<out Relative> =
        build(listOf(*parts))

      /**
       * The parts of the path must not contain / or \.
       */
      @JvmStatic
      fun build(parts: List<String>): IjentPathResult<out Relative> =
        ArrayListIjentRelativePath.build(parts)
    }

    override val parent: Relative?

    /** See [java.nio.file.Path.startsWith] */
    fun startsWith(other: Relative): Boolean

    override fun resolve(other: Relative): IjentPathResult<out Relative>

    override fun getChild(name: String): IjentPathResult<out Relative>

    override fun compareTo(other: Relative): Int

    /**
     * Resolves special path elements like "." and ".." whenever it is possible.
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
  interface Absolute : IjentPath, Comparable<Absolute> {
    companion object {
      @JvmStatic
      fun parse(raw: String, os: OS?): IjentPathResult<out Absolute> =
        ArrayListIjentAbsolutePath.parse(raw, os)

      @JvmStatic
      fun build(vararg parts: String): IjentPathResult<out Absolute> =
        build(listOf(*parts), null)

      @JvmStatic
      fun build(parts: List<String>, os: OS?): IjentPathResult<out Absolute> =
        ArrayListIjentAbsolutePath.build(parts, os)
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
    fun normalize(): IjentPathResult<out Absolute>

    /** See [java.nio.file.Path.resolve] */
    override fun resolve(other: Relative): IjentPathResult<out Absolute>

    /**
     * See [java.nio.file.Path.relativize].
     *
     * ```kotlin
     * IjentPathAbsolute.parse("C:\\foo\\bar\\baz", isWindows = true).relativize(IjentPathAbsolute.parse("C:\\foo\\oops", isWindows = true))
     *   == IjentPathAbsolute.parse("..\..\oops", isWindows = true)
     * ```
     */
    fun relativize(other: Absolute): IjentPathResult<out Relative>

    override fun getChild(name: String): IjentPathResult<out Absolute>

    fun scan(): Sequence<Absolute>

    /** See [java.nio.file.Path.toString] */
    override fun toString(): String

    /** TODO Describe the difference with [toString] */
    fun toDebugString(): String
  }
}