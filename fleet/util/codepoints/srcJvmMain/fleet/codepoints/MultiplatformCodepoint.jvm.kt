package fleet.codepoints

import fleet.util.multiplatform.Actual

@Actual
internal fun codePointsToStringPlatformSpecificJvm(vararg codepoints: Int): String {
  return String(codepoints, 0, codepoints.size)
}

@Actual
internal fun codepointOfPlatformSpecificJvm(highSurrogate: Char, lowSurrogate: Char): Codepoint {
  return Codepoint(Character.toCodePoint(highSurrogate, lowSurrogate))
}

@Actual
internal fun highSurrogatePlatformSpecificJvm(codepoint: Int): Char = Character.highSurrogate(codepoint)

@Actual
internal fun lowSurrogatePlatformSpecificJvm(codepoint: Int): Char = Character.lowSurrogate(codepoint)