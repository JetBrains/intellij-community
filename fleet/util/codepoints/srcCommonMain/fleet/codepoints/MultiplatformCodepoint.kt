package fleet.codepoints

import fleet.util.multiplatform.linkToActual

internal const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
private const val MIN_HIGH_SURROGATE = 0xD800
private const val MIN_LOW_SURROGATE = 0xDC00
private const val SURROGATE_DECODE_OFFSET = MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE shl 10) - MIN_LOW_SURROGATE
private const val HIGH_SURROGATE_ENCODE_OFFSET = (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT ushr 10))

internal fun codePointsToStringPlatformSpecific(vararg codepoints: Int): String = linkToActual()

internal fun codePointsToStringMultiplatform(vararg codepoints: Int): String {
  return buildString(capacity = codepoints.size * 2) {
    for (codePoint in codepoints) {
      appendCodePoint(Codepoint(codePoint))
    }
  }
}

internal fun codepointOfPlatformSpecific(highSurrogate: Char, lowSurrogate: Char): Codepoint = linkToActual()

internal fun codepointOfMultiplatform(highSurrogate: Char, lowSurrogate: Char): Codepoint {
  return Codepoint((highSurrogate.code shl 10) + lowSurrogate.code + SURROGATE_DECODE_OFFSET)
}

internal fun highSurrogatePlatformSpecific(codepoint: Int): Char = linkToActual()

internal fun highSurrogateMultiplatform(codepoint: Int): Char {
  return ((codepoint ushr 10) + HIGH_SURROGATE_ENCODE_OFFSET).toChar()
}

internal fun lowSurrogatePlatformSpecific(codepoint: Int): Char = linkToActual()

internal fun lowSurrogateMultiplatform(codepoint: Int): Char {
  return ((codepoint and 0x3FF) + MIN_LOW_SURROGATE).toChar()
}