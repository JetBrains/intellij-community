package fleet.codepoints

@fleet.util.multiplatform.Actual
internal fun codePointsToStringPlatformSpecificWasmJs(vararg codepoints: Int): String {
  return codePointsToStringMultiplatform(*codepoints)
}

@fleet.util.multiplatform.Actual
internal fun codepointOfPlatformSpecificWasmJs(highSurrogate: Char, lowSurrogate: Char): Codepoint {
  return codepointOfMultiplatform(highSurrogate, lowSurrogate)
}

@fleet.util.multiplatform.Actual
internal fun highSurrogatePlatformSpecificWasmJs(codepoint: Int): Char = highSurrogateMultiplatform(codepoint)

@fleet.util.multiplatform.Actual
internal fun lowSurrogatePlatformSpecificWasmJs(codepoint: Int): Char = lowSurrogateMultiplatform(codepoint)