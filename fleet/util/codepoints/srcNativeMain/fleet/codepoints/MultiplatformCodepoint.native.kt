package fleet.codepoints

@fleet.util.multiplatform.Actual
internal fun codePointsToStringPlatformSpecificNative(vararg codepoints: Int): String {
  return codePointsToStringMultiplatform(*codepoints)
}

@fleet.util.multiplatform.Actual
internal fun codepointOfPlatformSpecificNative(highSurrogate: Char, lowSurrogate: Char): Codepoint {
  return codepointOfMultiplatform(highSurrogate, lowSurrogate)
}

@fleet.util.multiplatform.Actual
internal fun highSurrogatePlatformSpecificNative(codepoint: Int): Char = highSurrogateMultiplatform(codepoint)

@fleet.util.multiplatform.Actual
internal fun lowSurrogatePlatformSpecificNative(codepoint: Int): Char = lowSurrogateMultiplatform(codepoint