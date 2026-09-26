package fleet.codepoints

fun Sequence<Codepoint>.asString(): String = buildString {
    for (codepoint in this@asString) {
        appendCodePoint(codepoint)
    }
}
