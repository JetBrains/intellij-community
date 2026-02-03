package fleet.codepoints

fun <T : Appendable> T.appendCodePoint(codepoint: Codepoint): T {
  if (codepoint.isBmpCodePoint()) {
    append(codepoint.codepoint.toChar())
  }
  else {
    append(highSurrogate(codepoint.codepoint))
    append(lowSurrogate(codepoint.codepoint))
  }
  return this
}
