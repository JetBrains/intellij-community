// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ijent.fs.IjentPathResult.Err
import com.intellij.platform.ijent.fs.IjentPathResult.Ok

internal class ArrayListIjentAbsolutePath private constructor(
  private val _root: Root,
  private val parts: List<String>,
) : IjentPath.Absolute {
  init {
    // TODO To be removed when the class is thoroughly covered with unit tests.
    require(parts.all(String::isNotEmpty)) { "An empty string in the path parts: $parts" }
  }

  override val os: IjentPath.Absolute.OS
    get() = when (_root) {
      Root.Unix -> IjentPath.Absolute.OS.UNIX
      is Root.Windows -> IjentPath.Absolute.OS.WINDOWS
    }

  override val root: IjentPath.Absolute by lazy {
    if (parts.isEmpty()) this
    else ArrayListIjentAbsolutePath(_root, listOf())
  }

  override val parent: IjentPath.Absolute?
    get() =
      if (parts.isEmpty()) null
      else ArrayListIjentAbsolutePath(_root, parts.dropLast(1))

  override fun startsWith(other: IjentPath.Absolute): Boolean =
    nameCount >= other.nameCount &&
    root.fileName == other.fileName &&
    (0..<nameCount).all { getName(it) == other.getName(it) }

  override fun normalize(): IjentPathResult<out IjentPath.Absolute> {
    val result = mutableListOf<String>()
    for (part in parts) {
      when (part) {
        "." -> Unit

        ".." ->
          if (result.isEmpty())
            return Err(toString(), "Traversing beyond the root")
          else
            result.dropLast(1)

        else ->
          result += part
      }
    }
    return Ok(ArrayListIjentAbsolutePath(_root, result))
  }

  override fun resolve(other: IjentPath.Relative): IjentPathResult<out IjentPath.Absolute> {
    val result = parts.toMutableList()
    for (index in 0..<other.nameCount) {
      val name = other.getName(index).fileName
      val error = checkFileName(name)
      if (error != null) return Err(other.toString(), error)
      result += name
    }
    return Ok(ArrayListIjentAbsolutePath(_root, result))
  }

  override fun getChild(name: String): IjentPathResult<out IjentPath.Absolute> {
    val error = checkFileName(name)
    return if (error == null)
      Ok(ArrayListIjentAbsolutePath(_root, parts + name))
    else
      Err(name, error)
  }

  override fun scan(): Sequence<IjentPath.Absolute> =
    parts.asSequence().scan(ArrayListIjentAbsolutePath(_root, listOf())) { parent, name ->
      ArrayListIjentAbsolutePath(_root, parent.parts + name)
    }

  override fun toString(): String =
    when (_root) {
      Root.Unix -> parts.joinToString(separator = "/", prefix = "/")

      is Root.Windows -> parts.joinToString(
        separator = "\\",
        prefix = run {
          if (_root.name.endsWith("\\")) _root.name
          else _root.name + "\\"
        }
      )
    }

  override fun toDebugString(): String =
    "${javaClass.simpleName}(_root=$root, parts=$parts)"

  override val fileName: String
    get() = parts.lastOrNull() ?: _root.name

  override val nameCount: Int
    get() = parts.size

  override fun getName(index: Int): IjentPath.Relative {
    require(index in parts.indices) { "$index !in ${parts.indices}" }
    return IjentPath.Relative.build(parts[index]).getOrThrow()
  }

  override fun endsWith(other: IjentPath.Relative): Boolean {
    val diff = nameCount - other.nameCount
    return diff >= 0 &&
           root.fileName == other.fileName &&
           (0..<other.nameCount).all { getName(it) == other.getName(it + diff) }
  }

  override fun compareTo(other: IjentPath.Absolute): Int {
    run {
      val cmp = root.fileName.compareTo(other.root.fileName)
      if (cmp != 0) {
        return cmp
      }
    }

    for (index in 0..<nameCount.coerceAtMost(other.nameCount)) {
      val cmp = getName(index).compareTo(other.getName(index))
      if (cmp != 0) {
        return cmp
      }
    }

    return nameCount - other.nameCount
  }

  override fun relativize(other: IjentPath.Absolute): IjentPathResult<out IjentPath.Relative> {
    if (root != other.root) {
      return Err(other.root.toString(), "The other path has a different root")
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
      result += other.getName(index).fileName
    }

    return IjentPath.Relative.build(result)
  }

  override fun equals(other: Any?): Boolean =
    other is IjentPath.Absolute &&
    nameCount == other.nameCount &&
    root.fileName == other.root.fileName &&
    (0..<nameCount).all { getName(it) == other.getName(it) }

  override fun hashCode(): Int =
    31 * _root.hashCode() + parts.hashCode()

  private fun checkFileName(name: String): String? =
    checkFileName(name, isWindows = when (_root) {
      Root.Unix -> false
      is Root.Windows -> true
    })

  companion object {
    fun build(parts: List<String>, os: IjentPath.Absolute.OS?): IjentPathResult<out IjentPath.Absolute> {
      require(parts.isNotEmpty()) { "Can't build an absolute path from no path parts" }

      val windowsRoot = when (os) {
        IjentPath.Absolute.OS.WINDOWS, null -> findAbsoluteUncPath(parts.first()) ?: findAbsoluteTraditionalDosPath(parts.first())
        IjentPath.Absolute.OS.UNIX -> null
      }
      when (windowsRoot) {
        null -> {
          // An API user may expect that if a Windows path factory always requires to specify a root, then a Unix path factory requires
          // the same. Another API user may expect that since the Unix path is always know and since slashes are prohibited inside file
          // names, the root shouldn't be specified explicitly. Luckily, it's easy to meet expectations of both users.
          @Suppress("NAME_SHADOWING") val parts =
            if (parts.first() == "/") parts.drop(1)
            else parts
          for (part in parts) {
            val error = checkFileName(part, isWindows = false)
            if (error != null) return Err(part, error)
          }
          return Ok(ArrayListIjentAbsolutePath(Root.Unix, parts))
        }

        is Ok -> {
          @Suppress("NAME_SHADOWING") val parts = parts.drop(1)
          for (part in parts) {
            val error = checkFileName(part, isWindows = true)
            if (error != null) return Err(part, error)
          }
          return Ok(ArrayListIjentAbsolutePath(windowsRoot.path._root, parts))
        }

        is Err -> return windowsRoot
      }
    }

    fun parse(raw: String, os: IjentPath.Absolute.OS?): IjentPathResult<ArrayListIjentAbsolutePath> =
      when (os) {
        IjentPath.Absolute.OS.WINDOWS ->
          findAbsoluteUncPath(raw)
          ?: findAbsoluteTraditionalDosPath(raw)
          ?: reportError(raw)

        IjentPath.Absolute.OS.UNIX ->
          findAbsoluteUnixPath(raw)
          ?: reportError(raw)

        null ->
          findAbsoluteUncPath(raw)
          ?: findAbsoluteTraditionalDosPath(raw)
          ?: findAbsoluteUnixPath(raw)
          ?: reportError(raw)
      }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#unc-paths */
    private fun findAbsoluteUncPath(raw: String): IjentPathResult<ArrayListIjentAbsolutePath>? {
      if (raw.length < 3) return null
      if (raw.getOrNull(0) != raw.getOrNull(1)) return null

      var index = 2
      // Skipping slashes.
      while (raw[index] in "/\\") {
        if (++index == raw.length) return null
      }

      run {
        val error = checkFileName(raw.substring(2, index), isWindows = true)
        if (error != null) return Err(raw, "Incorrect server name in UNC path")
      }

      val shareNameStart = index

      if (++index == raw.length) return Err(raw, "Empty share name in UNC path")

      // Skipping the server/host name.
      while (raw[index] !in "/\\") {
        if (++index == raw.length) break
      }

      run {
        val error = checkFileName(raw.substring(shareNameStart, index), isWindows = true)
        if (error != null) return Err(raw, "Incorrect share name in UNC path")
      }

      val parts = raw.substring(index)
        .splitToSequence('/', '\\')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = true)
        if (error != null) return Err(raw, error)
      }

      return Ok(ArrayListIjentAbsolutePath(Root.Windows(raw.substring(0, index).replace("/", "\\")), parts))
    }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#traditional-dos-paths */
    private fun findAbsoluteTraditionalDosPath(raw: String): IjentPathResult<ArrayListIjentAbsolutePath>? {
      if (raw.length < 3) return null
      if (!raw[0].isLetter()) return null
      if (raw[1] != ':') return null
      if (raw[2] != '\\') return null

      val parts = raw.substring(3)
        .splitToSequence('/', '\\')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = true)
        if (error != null) return Err(raw, error)
      }

      return Ok(ArrayListIjentAbsolutePath(Root.Windows(raw.substring(0, 3)), parts))
    }

    private fun findAbsoluteUnixPath(raw: String): IjentPathResult<ArrayListIjentAbsolutePath>? {
      if (raw.getOrNull(0) != '/') return null

      val parts = raw
        .splitToSequence('/')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = false)
        if (error != null) return Err(raw, error)
      }

      return Ok(ArrayListIjentAbsolutePath(Root.Unix, parts))
    }

    private fun reportError(raw: String): Err<ArrayListIjentAbsolutePath> =
      Err(
        raw = raw,
        reason = run {
          if (raw.isEmpty())
            "Empty path"
          else {
            logger<ArrayListIjentRelativePath>().error("Failed to parse a path: $raw")
            "Unknown error during parsing"
          }
        }
      )

    private fun checkFileName(name: String, isWindows: Boolean): String? {
      // TODO There are many more invalid paths for Windows.
      val invalidSymbols =
        if (isWindows) "\u0000/\\:"
        else "\u0000/"
      for (symbol in invalidSymbols) {
        if (symbol in name) {
          val prettySymbol =
            if (symbol.isISOControl()) "\\u${symbol.code.toString(16).padStart(4, '0')}"
            else symbol
          return if (isWindows) {
            "Invalid symbol in Windows path: $prettySymbol"
          }
          else {
            "Invalid symbol in Unix path: $prettySymbol"
          }
        }
      }
      return null
    }

    private sealed interface Root {
      val name: String

      data object Unix : Root {
        override val name: String = "/"
      }

      data class Windows(override val name: String) : Root {
        init {
          require('/' !in name) { "Windows drives should not contain regular slashes" }
          require("\\" in name) {
            "Windows drive names must end with a backslash."
            // Otherwise, Windows treat such path as a current directory. Try `Files.list(Path.of("C:")).toList()` or `dir C:` in cmd.exe.
            // UNC server and share names may have no backslash at the end, but UNC labels always contain at least three backslashes.
          }
        }
      }
    }
  }
}