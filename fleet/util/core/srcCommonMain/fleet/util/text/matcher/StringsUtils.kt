// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlin.math.max
import kotlin.math.min

/**
 * Copy of com.intellij.openapi.util.text.Strings.indexOfAny(java.lang.CharSequence, java.lang.String, int, int)
 */
internal fun CharSequence.indexOfAny(chars: String, start: Int = 0, end: Int = length): Int {
  if (chars.isEmpty()) return -1;

  var end = min(end, length)
  for (i in max(start, 0)..<end) {
    if (chars.contains(this[i])) return i;
  }

  return -1;
}

internal fun CharSequence.indexOf(c: Char, start: Int = 0, end: Int = length): Int {
  var end = min(end, length);
  for (i in max(start, 0)..<end) {
    if (get(i) == c) return i
  }
  return -1
}

internal fun CharArray.indexOf(c: Char, startIndex: Int = 0, endIndex: Int = size, ignoreCase: Boolean = false): Int {
  var end = min(endIndex, size);
  for (i in max(startIndex, 0)..<end) {
    if (get(i).equals(c, ignoreCase = true)) return i
  }
  return -1
}



/**
 * Copy of com.intellij.util.text.CharArrayUtil.regionMatches(char[], int, int, java.lang.CharSequence)
 */
internal fun CharArray.regionMatches(start: Int, end: Int, other: CharSequence): Boolean {
  val len = other.length
  if (start + len > end) return false;
  if (start < 0) return false;
  for (i in 0 until len) {
    if (this[start + i] != other[i]) return false;
  }
  return true;
}


/**
 * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case-insensitive
 */
internal fun CharSequence.indexOfIgnoreCase(
  what: CharSequence,
  startOffset: Int = 0,
  endOffset: Int = what.length
): Int {
  var startOffset = startOffset
  if (endOffset < startOffset) {
    return -1
  }

  val targetCount = what.length
  val sourceCount = length

  if (startOffset >= sourceCount) {
    return if (targetCount == 0) sourceCount else -1
  }

  if (startOffset < 0) {
    startOffset = 0
  }

  if (targetCount == 0) {
    return startOffset
  }

  val first = what.get(0)
  val max = endOffset - targetCount

  var i = startOffset
  while (i <= max) {
    /* Look for first character. */
    if (!get(i).equals(first, ignoreCase = true)) {
      while ((++i <= max) && !this[i].equals(first, ignoreCase = true)) {
        // No-op
      }
    }

    /* Found first character, now look at the rest of v2 */
    if (i <= max) {
      var j = i + 1
      val end = j + targetCount - 1
      var k = 1
      while (j < end && this[j].equals(what[k], ignoreCase = true)) {
        j++
        k++
      }

      if (j == end) {
        /* Found whole string. */
        return i
      }
    }
    i++
  }

  return -1
}
