// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import kotlin.Throws


internal class ArrayListEelRelativePath private constructor(
  private val parts: List<String>,
) : EelPath.Relative {
  init {
    // TODO To be removed when the class is thoroughly covered with unit tests.
    require(parts.all(String::isNotEmpty)) { "An empty string in the path parts: $parts" }
  }

  override val parent: EelPath.Relative?
    get() =
      if (parts.size < 2) null
      else ArrayListEelRelativePath(parts.dropLast(1))

  override fun startsWith(other: EelPath.Relative): Boolean {
    if (nameCount < other.nameCount) return false
    for ((index, part) in parts.withIndex()) {
      if (part != other.getName(index).fileName) {
        return false
      }
    }
    return true
  }

  override fun resolve(other: EelPath.Relative): ArrayListEelRelativePath {
    val result = mutableListOf<String>()
    result += parts
    if (other != EMPTY) {
      for (i in 0..<other.nameCount) {
        val fileName = other.getName(i).fileName
        result += fileName
      }
    }
    return ArrayListEelRelativePath(result)
  }

  override fun getChild(name: String): ArrayListEelRelativePath =
    when {
      name.isEmpty() -> throw EelPathException(name, "Empty child name is not allowed")
      "/" in name -> throw EelPathException(name, "Invalid symbol in child name: /")
      else -> ArrayListEelRelativePath(parts + name)
    }

  override fun compareTo(other: EelPath.Relative): Int {
    for (i in 0..<nameCount.coerceAtMost(other.nameCount)) {
      val comparison = getName(i).fileName.compareTo(other.getName(i).fileName)
      if (comparison != 0) return comparison
    }
    return nameCount - other.nameCount
  }

  override fun normalize(): EelPath.Relative {
    val result = mutableListOf<String>()
    for (part in parts) {
      when (part) {
        "." -> Unit

        ".." ->
          when (result.lastOrNull()) {
            "..", null -> result += ".."
            else -> result.removeLast()
          }

        else -> {
          result += part
        }
      }
    }
    return ArrayListEelRelativePath(result)
  }

  override val fileName: String
    get() = parts.lastOrNull().orEmpty()

  override val nameCount: Int
    get() = parts.count().coerceAtLeast(1)

  override fun getName(index: Int): EelPath.Relative {
    val newParts =
      if (parts.isEmpty()) listOf()
      else listOf(parts.getOrNull(index) ?: throw IllegalArgumentException("$index !in 0..<${parts.size}"))
    return ArrayListEelRelativePath(newParts)
  }

  override fun endsWith(other: EelPath.Relative): Boolean {
    if (nameCount < other.nameCount) return false
    var otherIndex = other.nameCount - 1
    for (part in parts.asReversed()) {
      if (part != other.getName(otherIndex).fileName) {
        return false
      }
      --otherIndex
    }
    return true
  }

  override fun toString(): String = parts.joinToString("/")

  override fun equals(other: Any?): Boolean =
    other is EelPath.Relative && other.compareTo(this) == 0

  override fun hashCode(): Int =
    parts.hashCode()

  companion object {
    val EMPTY = ArrayListEelRelativePath(listOf())
    private val REGEX = Regex("""[/\\]""")

    @Throws(EelPathException::class)
    fun parse(raw: String): ArrayListEelRelativePath =
      build(raw.splitToSequence(REGEX).filter(String::isNotEmpty).iterator())

    @Throws(EelPathException::class)
    fun build(parts: List<String>): ArrayListEelRelativePath =
      build(parts.iterator())

    @Throws(EelPathException::class)
    private fun build(parts: Iterator<String>): ArrayListEelRelativePath {
      // Not optimal, but DRY.
      var result = ArrayListEelRelativePath(listOf())
      for (part in parts) {
        result = result.getChild(part)
      }
      return result
    }
  }
}