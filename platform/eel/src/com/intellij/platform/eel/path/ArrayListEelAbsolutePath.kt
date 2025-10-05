// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.directorySeparators

internal class ArrayListEelAbsolutePath private constructor(
  override val descriptor: EelDescriptor,
  private val _root: Root,
  override val parts: List<String>,
) : EelPath, Comparable<EelPath> {

  override val root: EelPath by lazy {
    if (parts.isEmpty()) this
    else ArrayListEelAbsolutePath(descriptor, _root, listOf())
  }

  override val parent: EelPath?
    get() =
      if (parts.isEmpty()) null
      else ArrayListEelAbsolutePath(descriptor, _root, parts.dropLast(1))

  override fun startsWith(other: EelPath): Boolean =
    nameCount >= other.nameCount &&
    root.fileName == other.root.fileName &&
    (0..<other.nameCount).all { getName(it) == other.getName(it) }

  override fun endsWith(suffix: List<String>): Boolean {
    return nameCount >= suffix.size && this.parts.takeLast(suffix.size) == suffix
  }

  override fun normalize(): EelPath {
    val result = mutableListOf<String>()
    for (part in parts) {
      when (part) {
        "." -> Unit

        ".." -> if (result.isNotEmpty() && result.last() != "..") {
          // going beyond root is a no-op operation
          result.removeLast()
        }

        else ->
          result += part
      }
    }
    return ArrayListEelAbsolutePath(descriptor, _root, result)
  }

  override fun resolve(other: String): EelPath {
    val delimiters = this.platform.directorySeparators
    val otherParts = other.split(*delimiters).filter(String::isNotEmpty)
    for (name in otherParts) {
      if (name.isNotEmpty()) {
        val error = checkFileName(name)
        if (error != null) throw EelPathException(other.toString(), error)
      }
    }
    val newList = parts + otherParts
    return ArrayListEelAbsolutePath(descriptor, _root, newList)
  }

  override fun getChild(name: String): EelPath {
    require(name.isNotEmpty()) { "Child name must not be empty" }
    val error = checkFileName(name)
    return if (error == null)
      ArrayListEelAbsolutePath(descriptor, _root, parts + name)
    else
      throw EelPathException(name, error)
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

  override fun getName(index: Int): String {
    return parts[index]
  }

  override fun compareTo(other: EelPath): Int {
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

  override fun equals(other: Any?): Boolean =
    other is EelPath &&
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
    fun build(parts: List<String>, descriptor: EelDescriptor): EelPath {
      require(parts.isNotEmpty()) { "Can't build an absolute path from no path parts" }

      val windowsRoot = when (descriptor.osFamily) {
        EelOsFamily.Windows -> findAbsoluteUncPath(parts.first(), descriptor) ?: findAbsoluteTraditionalDosPath(parts.first(), descriptor)
        EelOsFamily.Posix -> null
      }
      when (windowsRoot) {
        null -> {
          // An API user may expect that if a Windows path factory always requires specifying a root, then a Unix path factory requires
          // the same.
          // Another API user may expect that since the Unix path is always known and since slashes are prohibited inside file
          // names, the root shouldn't be specified explicitly.
          // Luckily, it's easy to meet the expectations of both users.
          @Suppress("NAME_SHADOWING") val parts =
            if (parts.first() == "/") parts.drop(1)
            else parts
          for (part in parts) {
            val error = checkFileName(part, isWindows = false)
            if (error != null) throw EelPathException(part, error)
          }
          return ArrayListEelAbsolutePath(descriptor, Root.Unix, parts)
        }

        else -> {
          @Suppress("NAME_SHADOWING") val parts = parts.drop(1)
          for (part in parts) {
            val error = checkFileName(part, isWindows = true)
            if (error != null) throw EelPathException(part, error)
          }
          return ArrayListEelAbsolutePath(descriptor, windowsRoot._root, parts)
        }
      }
    }

    @Throws(EelPathException::class)
    fun parseOrNull(raw: String, descriptor: EelDescriptor): ArrayListEelAbsolutePath? =
      when (descriptor.osFamily) {
        EelOsFamily.Windows -> findAbsoluteUncPath(raw, descriptor) ?: findAbsoluteTraditionalDosPath(raw, descriptor)
        EelOsFamily.Posix -> findAbsoluteUnixPath(raw, descriptor)
      }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#unc-paths */
    private fun findAbsoluteUncPath(raw: String, descriptor: EelDescriptor): ArrayListEelAbsolutePath? {
      if (raw.length < 3) return null
      if (raw.getOrNull(0) != raw.getOrNull(1)) return null

      var index = 2
      // Skipping slashes.
      while (raw[index] in "/\\") {
        if (++index == raw.length) return null
      }

      run {
        val error = checkFileName(raw.substring(2, index), isWindows = true)
        if (error != null) throw EelPathException(raw, "Incorrect server name in UNC path")
      }

      val shareNameStart = index

      if (++index == raw.length) throw EelPathException(raw, "Empty share name in UNC path")

      // Skipping the server/host name.
      while (raw[index] !in "/\\") {
        if (++index == raw.length) break
      }

      run {
        val error = checkFileName(raw.substring(shareNameStart, index), isWindows = true)
        if (error != null) throw EelPathException(raw, "Incorrect share name in UNC path")
      }

      val parts = raw.substring(index)
        .splitToSequence('/', '\\')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = true)
        if (error != null) throw EelPathException(raw, error)
      }

      return ArrayListEelAbsolutePath(descriptor, Root.Windows(raw.substring(0, index).replace("/", "\\")), parts)
    }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#traditional-dos-paths */
    @Throws(EelPathException::class)
    private fun findAbsoluteTraditionalDosPath(raw: String, descriptor: EelDescriptor): ArrayListEelAbsolutePath? {
      if (raw.length < 3) return null
      if (!raw[0].isLetter()) return null
      if (raw[1] != ':') return null
      if (raw[2] != '\\' && raw[2] != '/') return null

      val parts = raw.substring(3)
        .splitToSequence('/', '\\')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = true)
        if (error != null) throw EelPathException(raw, error)
      }

      val driveRoot = raw.substring(0, 3).replace('/', '\\') // sometimes we have DOS paths containing forward slashes from users or VFS
      return ArrayListEelAbsolutePath(descriptor, Root.Windows(driveRoot), parts)
    }

    private fun findAbsoluteUnixPath(raw: String, descriptor: EelDescriptor): ArrayListEelAbsolutePath? {
      if (raw.isEmpty() || raw[0] != '/') return null

      val parts = raw.split("/").mapNotNull { part ->
        if (part.isNotEmpty()) {
          val error = checkFileName(part, isWindows = false)
          if (error != null) throw EelPathException(raw, error)
          part
        }
        else {
          null
        }
      }

      return ArrayListEelAbsolutePath(descriptor, Root.Unix, parts)
    }

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
            // Otherwise, Windows treat such paths as a current directory.
            // Try `Files.list(Path.of("C:")).toList()` or `dir C:` in cmd.exe.
            // UNC server and share names may have no backslash at the end, but UNC labels always contain at least three backslashes.
          }
        }
      }
    }
  }
}