// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.IjentId

internal class IjentPathImpl internal constructor(
  override val ijentId: IjentId,
  override val isWindows: Boolean,
  private val parts: List<String>,
) : IjentPath {
  companion object {
    private fun parseToParts(raw: String, isWindows: Boolean): List<String> {
      val parts = mutableListOf<String>()

      if (!isWindows && raw.startsWith('/')) {
        parts += "/"
      }

      for (part in raw.splitToSequence(Regex(if (isWindows) "[/\\\\]" else "/"))) {
        if (part.isEmpty()) continue

        parts +=
          if (isWindows && parts.singleOrNull()?.isWindowsDrive == true)
            parts.removeLast() + "\\" + part
          else
            part
      }

      return parts
    }

    @JvmStatic
    fun parse(ijentId: IjentId, isWindows: Boolean, raw: String): IjentPathImpl =
      IjentPathImpl(ijentId, isWindows, parseToParts(raw, isWindows))
  }

  override val root: IjentPathImpl?
    get() =
      getRootPartsSize()
        .takeIf { idx -> idx > 0 }
        ?.let { idx ->
          // Mind that subList returns a lazy list which holds a reference to the original list.
          IjentPathImpl(ijentId, isWindows, parts.subList(0, idx).toList())
        }

  override val isAbsolute: Boolean
    get() = getRootPartsSize() > 0

  override val fileName: IjentPathImpl?
    get() = parts.lastOrNull()?.let { fileName -> IjentPathImpl(ijentId, isWindows, listOf(fileName)) }

  override val parent: IjentPathImpl?
    get() =
      if (getRootPartsSize() < parts.size)
        IjentPathImpl(ijentId, isWindows, parts.run { subList(0, size - 1).toList() })
      else
        null

  override val nameCount: Int
    get() = parts.size - (getRootPartsSize() - 1).coerceAtLeast(0)

  override fun getName(index: Int): IjentPathImpl =
    parts.getOrNull(index)?.let { name -> IjentPathImpl(ijentId, isWindows, listOf(name)) }
    ?: throw IllegalArgumentException("$index is not in ${parts.indices}")

  override fun subpath(beginIndex: Int, endIndex: Int): IjentPathImpl =
    IjentPathImpl(ijentId, isWindows, parts.subList(beginIndex, endIndex))

  override fun startsWith(other: IjentPath): Boolean {
    if (other !is IjentPathImpl) TODO()
    return ijentId == other.ijentId && parts startsWith other.parts
  }

  override fun startsWith(other: String): Boolean =
    parts startsWith parseToParts(other, isWindows)

  private infix fun <T> List<T>.startsWith(other: List<T>): Boolean =
    other.size <= parts.size &&
    parts.subList(0, other.size) == other

  override fun endsWith(other: IjentPath): Boolean {
    if (other !is IjentPathImpl) TODO()
    return ijentId == other.ijentId && parts endsWith other.parts
  }

  override fun endsWith(other: String): Boolean =
    parts endsWith parseToParts(other, isWindows)

  private infix fun <T> List<T>.endsWith(other: List<T>): Boolean =
    other.size <= parts.size &&
    parts.subList(parts.size - other.size, parts.size) == other

  override fun normalize(): IjentPathImpl {
    val result = mutableListOf<String>()

    val rootPartsSize = getRootPartsSize()

    for (part in parts) {
      when (part) {
        "", "." -> Unit

        ".." -> when {
          rootPartsSize > 0 && result.size <= rootPartsSize -> {
            result.clear()
            break
          }

          result.lastOrNull() == null || result.lastOrNull() == ".." -> result += part

          else -> result.removeLast()
        }

        else -> result += part
      }
    }

    return IjentPathImpl(ijentId, isWindows, result)
  }

  override fun resolve(other: IjentPath): IjentPath {
    if (other !is IjentPathImpl) TODO()
    require(ijentId == other.ijentId)  // Not actually important. Delete this line if it burdens.
    return if (other.isAbsolute)
      other
    else
      IjentPathImpl(ijentId, isWindows, parts + other.parts)
  }

  override fun resolve(other: String): IjentPath =
    resolve(parse(ijentId, isWindows, other))

  override fun resolveSibling(other: String): IjentPath =
    resolveSibling(parse(ijentId, isWindows, other))

  override fun iterator(): Iterator<IjentPathImpl> =
    parts.asSequence()
      .map { part -> IjentPathImpl(ijentId, isWindows, listOf(part)) }
      .iterator()

  override fun toString(): String =
    when {
      isWindows -> parts.joinToString(separator = "\\")
      parts.firstOrNull() == "/" -> parts.run { subList(1, size) }.joinToString(separator = "/", prefix = "/")
      else -> parts.joinToString(separator = "/")
    }

  override fun toDebugString(): String =
    "${javaClass.simpleName}($ijentId, ${if (isWindows) "win" else "unix"}, $this)"

  override fun compareTo(other: IjentPath): Int {
    if (other !is IjentPathImpl) TODO()

    val labelDiff = ijentId.id.compareTo(other.ijentId.id)
    if (labelDiff != 0) return labelDiff

    repeat(parts.size.coerceAtMost(other.parts.size)) { idx ->
      val diff = parts[idx].compareTo(other.parts[idx])
      if (diff != 0) return diff
    }

    return parts.size - other.parts.size
  }

  override fun equals(other: Any?): Boolean =
    other is IjentPathImpl
    && ijentId == other.ijentId
    && parts == other.parts

  override fun hashCode(): Int =
    ijentId.hashCode() * 31 + parts.hashCode()

  private fun getRootPartsSize(): Int =
    when {
      parts.isEmpty() -> 0

      isWindows && parts.first().isWindowsDrive -> 1

      !isWindows && parts.first() == "/" -> 1

      else -> 0
    }
}

private val String.isWindowsDrive: Boolean
  get() = length == 3 && endsWith(":\\") || startsWith("\\\\")
