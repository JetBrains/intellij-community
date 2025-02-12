// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.linkToActual

fun String.capitalizeWithCurrentLocale(): String = linkToActual()
fun String.lowercaseWithCurrentLocale(): String = linkToActual()
fun String.uppercaseWithCurrentLocale(): String = linkToActual()

fun String.capitalizeLocaleAgnostic(): String = replaceFirstChar {
  if (it.isLowerCase()) it.titlecase() else it.toString()
}

fun String.lowercaseLocaleAgnostic(): String = lowercase()
fun String.uppercaseLocaleAgnostic(): String = uppercase()

object LineEndings {
  const val CR = "\r"
  const val LF = "\n"
  const val CRLF = "\r\n"
}

fun String.toOsLineEndings(): String {
  return if (Os.INSTANCE.isWindows && this.contains(LineEndings.LF) && !this.contains(LineEndings.CRLF)) {
    this.replace(LineEndings.LF, LineEndings.CRLF)
  }
  else {
    this
  }
}

fun String.normalizeLineEndings(): String {
  return this.replace(LineEndings.CRLF, LineEndings.LF)
    .replace(LineEndings.CR, LineEndings.LF)
}

/**
 * Converts UTF-8 offset to UTF-16 offset.
 */
fun String.offset8to16(offset: Int): Int {
  var utf8Offset = offset
  var utf16Offset = 0
  for (codePoint in codePoints()) {
    utf8Offset -= when {
      codePoint < 128 -> 1
      codePoint < 2048 -> 2
      codePoint < 65536 -> 3
      else -> 4
    }

    utf16Offset += 1
    // Code points from the supplementary planes are encoded as a surrogate pair in utf-16,
    // meaning we'll have one extra utf-16 code unit for every code point in this range.
    if (codePoint >= 65536) utf16Offset += 1

    if (utf8Offset <= 0) break
  }

  return utf16Offset
}

fun String?.compareVersionNumbers(other: String?): Int {
  // todo duplicates com.intellij.openapi.util.text.StringUtil.compareVersionNumbers
  if (this == null && other == null) {
    return 0
  }
  if (this == null) {
    return -1
  }
  if (other == null) {
    return 1
  }
  val part1 = split("[._\\-]".toRegex())
  val part2 = other.split("[._\\-]".toRegex())
  var idx = 0
  while (idx < part1.size && idx < part2.size) {
    val p1 = part1[idx]
    val p2 = part2[idx]
    val cmp: Int = if (p1.matches("\\d+".toRegex()) && p2.matches("\\d+".toRegex())) {
      p1.toInt().compareTo(p2.toInt())
    }
    else {
      p1.compareTo(p2)
    }
    if (cmp != 0) return cmp
    idx++
  }
  if (part1.size != part2.size) {
    val left = part1.size > idx
    val parts = if (left) part1 else part2
    while (idx < parts.size) {
      val p = parts[idx]
      val cmp: Int = if (p.matches("\\d+".toRegex())) {
        p.toInt().compareTo(0)
      }
      else {
        1
      }
      if (cmp != 0) return if (left) cmp else -cmp
      idx++
    }
  }
  return 0
}

fun String.unescapeHtml(): String =
  replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace("&#39;", "'")
    .replace("&quot;", "\"")
