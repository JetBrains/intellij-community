package fleet.codepoints

/**
 * Converts UTF-8 offset to UTF-16 offset.
 */
fun String.offset8to16(offset: Int): Int {
  var utf8Offset = offset
  var utf16Offset = 0
  for (c in codepoints(0)) {
    val codePoint = c.codepoint
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
