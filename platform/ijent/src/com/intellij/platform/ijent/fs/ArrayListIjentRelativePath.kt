// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.util.SmartList
import com.intellij.util.asSafely

internal class ArrayListIjentRelativePath private constructor(
  private val parts: List<String>,
) : IjentPath.Relative {
  init {
    // TODO To be removed when the class is thoroughly covered with unit tests.
    require(parts.all(String::isNotEmpty)) { "An empty string in the path parts: $parts" }
  }

  override val parent: IjentPath.Relative?
    get() =
      if (parts.size < 2) null
      else ArrayListIjentRelativePath(parts.dropLast(1))

  override fun startsWith(other: IjentPath.Relative): Boolean {
    if (nameCount < other.nameCount) return false
    for ((index, part) in parts.withIndex()) {
      if (part != other.getName(index).fileName) {
        return false
      }
    }
    return true
  }

  override fun resolve(other: IjentPath.Relative): IjentPathResult<ArrayListIjentRelativePath> {
    val result = SmartList<String>()
    result += parts
    for (i in 0..<other.nameCount) {
      val fileName = other.getName(i).fileName
      result += fileName
    }
    return IjentPathResult.Ok(ArrayListIjentRelativePath(result))
  }

  override fun getChild(name: String): IjentPathResult<ArrayListIjentRelativePath> =
    when {
      name.isEmpty() -> IjentPathResult.Err(name, "Empty child name is not allowed")
      "/" in name -> IjentPathResult.Err(name, "Invalid symbol in child name: /")
      else -> IjentPathResult.Ok(ArrayListIjentRelativePath(parts + name))
    }

  override fun compareTo(other: IjentPath.Relative): Int {
    for (i in 0..<nameCount.coerceAtMost(other.nameCount)) {
      val comparison = getName(i).fileName.compareTo(other.getName(i).fileName)
      if (comparison != 0) return comparison
    }
    return nameCount - other.nameCount
  }

  override fun normalize(): IjentPath.Relative {
    val result = SmartList<String>()
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
    return ArrayListIjentRelativePath(result)
  }

  override val fileName: String
    get() = parts.lastOrNull().orEmpty()

  override val nameCount: Int
    get() = parts.count().coerceAtLeast(1)

  override fun getName(index: Int): IjentPath.Relative {
    val newParts =
      if (parts.isEmpty()) listOf()
      else listOf(parts.getOrNull(index) ?: throw IllegalArgumentException("$index !in 0..<${parts.size}"))
    return ArrayListIjentRelativePath(newParts)
  }

  override fun endsWith(other: IjentPath.Relative): Boolean {
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
    other?.asSafely<IjentPath.Relative>()?.compareTo(this) == 0

  override fun hashCode(): Int =
    parts.hashCode()

  companion object {
    fun parse(raw: String): IjentPathResult<ArrayListIjentRelativePath> =
      build(raw.splitToSequence(Regex("""[/\\]""")).filter(String::isNotEmpty).iterator())

    fun build(parts: List<String>): IjentPathResult<ArrayListIjentRelativePath> =
      build(parts.iterator())

    private fun build(parts: Iterator<String>): IjentPathResult<ArrayListIjentRelativePath> {
      // Not optimal, but DRY.
      var result = ArrayListIjentRelativePath(listOf())
      for (part in parts) {
        result = when (val r = result.getChild(part)) {
          is IjentPathResult.Ok -> r.path
          is IjentPathResult.Err -> return r
        }
      }
      return IjentPathResult.Ok(result)
    }
  }
}