package fleet.codepoints

fun <T : Appendable> T.appendCodePoint(codepoint: Codepoint): T {
  if (codepoint.isBmpCodePoint()) {
    append(codepoint.codepoint.toChar())
  }
  else {
    append(highSurrogatePlatformSpecific(codepoint.codepoint))
    append(lowSurrogatePlatformSpecific(codepoint.codepoint))
  }
  return this
}
