// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

internal class ArrayListEelAbsolutePath private constructor(
  private val _root: Root,
  override val parts: List<String>,
) : EelPath, Comparable<EelPath> {
  init {
    // TODO To be removed when the class is thoroughly covered with unit tests.
    require(parts.all(String::isNotEmpty)) { "An empty string in the path parts: $parts" }
  }

  override val root: EelPath by lazy {
    if (parts.isEmpty()) this
    else ArrayListEelAbsolutePath(_root, listOf())
  }

  override val parent: EelPath?
    get() =
      if (parts.isEmpty()) null
      else ArrayListEelAbsolutePath(_root, parts.dropLast(1))

  override fun startsWith(other: EelPath): Boolean =
    nameCount >= other.nameCount &&
    root.fileName == other.root.fileName &&
    (0..<other.nameCount).all { getName(it) == other.getName(it) }

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
    return ArrayListEelAbsolutePath(_root, result)
  }

  override fun resolve(other: String): EelPath {
    val otherParts = other.split('/', '\\')
    for (name in otherParts) {
      if (name.isNotEmpty()) {
        val error = checkFileName(name)
        if (error != null) throw EelPathException(other.toString(), error)
      }
    }
    val newList = parts + otherParts
    return ArrayListEelAbsolutePath(_root, newList)
  }

  override fun getChild(name: String): EelPath {
    val error = checkFileName(name)
    return if (error == null)
      ArrayListEelAbsolutePath(_root, parts + name)
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
    fun build(parts: List<String>, os: EelPath.OS?): EelPath {
      require(parts.isNotEmpty()) { "Can't build an absolute path from no path parts" }

      val windowsRoot = when (os) {
        EelPath.OS.WINDOWS, null -> findAbsoluteUncPath(parts.first()) ?: findAbsoluteTraditionalDosPath(parts.first())
        EelPath.OS.UNIX -> null
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
          return ArrayListEelAbsolutePath(Root.Unix, parts)
        }

        else -> {
          @Suppress("NAME_SHADOWING") val parts = parts.drop(1)
          for (part in parts) {
            val error = checkFileName(part, isWindows = true)
            if (error != null) throw EelPathException(part, error)
          }
          return ArrayListEelAbsolutePath(windowsRoot._root, parts)
        }
      }
    }

    @Throws(EelPathException::class)
    fun parseOrNull(raw: String, os: EelPath.OS?): ArrayListEelAbsolutePath? =
      when (os) {
        EelPath.OS.WINDOWS -> findAbsoluteUncPath(raw) ?: findAbsoluteTraditionalDosPath(raw)
        EelPath.OS.UNIX -> findAbsoluteUnixPath(raw)
        null ->
          findAbsoluteUncPath(raw)
          ?: findAbsoluteTraditionalDosPath(raw)
          ?: findAbsoluteUnixPath(raw)
      }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#unc-paths */
    private fun findAbsoluteUncPath(raw: String): ArrayListEelAbsolutePath? {
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

      return ArrayListEelAbsolutePath(Root.Windows(raw.substring(0, index).replace("/", "\\")), parts)
    }

    /** https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#traditional-dos-paths */
    @Throws(EelPathException::class)
    private fun findAbsoluteTraditionalDosPath(raw: String): ArrayListEelAbsolutePath? {
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
      return ArrayListEelAbsolutePath(Root.Windows(driveRoot), parts)
    }

    private fun findAbsoluteUnixPath(raw: String): ArrayListEelAbsolutePath? {
      if (raw.getOrNull(0) != '/') return null

      val parts = raw
        .splitToSequence('/')
        .filter(String::isNotEmpty)
        .toList()

      for (part in parts) {
        val error = checkFileName(part, isWindows = false)
        if (error != null) throw EelPathException(raw, error)
      }

      return ArrayListEelAbsolutePath(Root.Unix, parts)
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