package fleet.codepoints

import fleet.codepoints.generated.CharacterData
import fleet.codepoints.generated.ScriptData

/**
 * Unicode character property functions.
 */
internal object MultiplatformCodepoints {
  private const val MIN_HIGH_SURROGATE = 0xD800
  private const val MIN_LOW_SURROGATE = 0xDC00
  private const val SURROGATE_DECODE_OFFSET =
    MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE shl 10) - MIN_LOW_SURROGATE
  private const val HIGH_SURROGATE_ENCODE_OFFSET = (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT ushr 10))

    // Pre-computed masks for efficient multi-category checks
    private const val LETTER_MASK = (1 shl CharacterData.CAT_LU) or (1 shl CharacterData.CAT_LL) or
        (1 shl CharacterData.CAT_LT) or (1 shl CharacterData.CAT_LM) or (1 shl CharacterData.CAT_LO)
    private const val LETTER_OR_DIGIT_MASK = LETTER_MASK or (1 shl CharacterData.CAT_ND)
    private const val SPACE_CHAR_MASK = (1 shl CharacterData.CAT_ZS) or (1 shl CharacterData.CAT_ZL) or
        (1 shl CharacterData.CAT_ZP)

    // Helper functions for property interpretation
    private fun getCategory(props: Int): Int =
        (props and CharacterData.CATEGORY_MASK) ushr CharacterData.CATEGORY_SHIFT

    private fun getCaseDelta(props: Int): Int {
        // Decode signed 10-bit case delta from packed properties.
        // Values 0x000-0x1FF are positive (0 to 511), 0x200-0x3FF are negative (-512 to -1)
        val delta = props and CharacterData.CASE_DELTA_MASK
        return if (delta >= 0x200) delta - 0x400 else delta
    }

    private fun isDeltaToLowercase(props: Int): Boolean =
        (props and CharacterData.DELTA_TO_LOWERCASE_BIT) != 0

    private fun hasLargeLowercaseDelta(props: Int): Boolean =
        (props and CharacterData.HAS_LARGE_LOWERCASE_DELTA_BIT) != 0

    private fun hasLargeUppercaseDelta(props: Int): Boolean =
        (props and CharacterData.HAS_LARGE_UPPERCASE_DELTA_BIT) != 0

  fun codepointsToString(vararg codepoints: Int): String = buildString(capacity = codepoints.size * 2) {
    for (codePoint in codepoints) {
      appendCodePoint(Codepoint(codePoint))
    }
  }

  fun codepointOf(highSurrogate: Char, lowSurrogate: Char): Codepoint =
    Codepoint((highSurrogate.code shl 10) + lowSurrogate.code + SURROGATE_DECODE_OFFSET)

  fun highSurrogate(codepoint: Int): Char = ((codepoint ushr 10) + HIGH_SURROGATE_ENCODE_OFFSET).toChar()
  fun lowSurrogate(codepoint: Int): Char = ((codepoint and 0x3FF) + MIN_LOW_SURROGATE).toChar()

  fun isLetter(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return ((1 shl getCategory(props)) and LETTER_MASK) != 0
    }

    fun isDigit(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return getCategory(props) == CharacterData.CAT_ND
    }

    fun isLetterOrDigit(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return ((1 shl getCategory(props)) and LETTER_OR_DIGIT_MASK) != 0
    }

    fun isUpperCase(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return getCategory(props) == CharacterData.CAT_LU ||
            (props and CharacterData.IS_OTHER_UPPERCASE_BIT) != 0
    }

    fun isLowerCase(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return getCategory(props) == CharacterData.CAT_LL ||
            (props and CharacterData.IS_OTHER_LOWERCASE_BIT) != 0
    }

    fun toLowerCase(codepoint: Int): Int {
        if (isAscii(codepoint)) {
            return asciiToLowerCase(codepoint)
        }

        val props = CharacterData.getProperties(codepoint)

        if (hasLargeLowercaseDelta(props)) {
            return codepoint + binarySearchRange(codepoint, CharacterData.largeLowercaseRanges, 0)
        }

        val delta = getCaseDelta(props)
        return if (delta != 0 && isDeltaToLowercase(props)) {
            codepoint + delta
        } else {
            codepoint
        }
    }

    fun toUpperCase(codepoint: Int): Int {
        if (isAscii(codepoint)) {
            return asciiToUpperCase(codepoint)
        }

        val props = CharacterData.getProperties(codepoint)

        // Special handling for titlecase letters (Lt) - these map to their uppercase variants
        // U+01C5 Dž -> U+01C4 DŽ, U+01C8 Lj -> U+01C7 LJ
        // U+01CB Nj -> U+01CA NJ, U+01F2 Dz -> U+01F1 DZ
        if (getCategory(props) == CharacterData.CAT_LT) {
            return when (codepoint) {
                0x01C5, 0x01C8, 0x01CB, 0x01F2 -> codepoint - 1
                else -> codepoint
            }
        }

        if (hasLargeUppercaseDelta(props)) {
            return codepoint + binarySearchRange(codepoint, CharacterData.largeUppercaseRanges, 0)
        }

        val delta = getCaseDelta(props)
        return if (delta != 0 && !isDeltaToLowercase(props)) {
            codepoint + delta
        } else {
            codepoint
        }
    }

    fun isSpaceChar(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return ((1 shl getCategory(props)) and SPACE_CHAR_MASK) != 0
    }

    fun isWhitespace(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_WHITESPACE_BIT) != 0
    }

    fun isIdeographic(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_IDEOGRAPHIC_BIT) != 0
    }

    fun isIdentifierIgnorable(codepoint: Int): Boolean {
        // Control characters that are ignorable in identifiers:
        // U+0000..U+0008: <control> (NUL through BACKSPACE)
        // U+000E..U+001B: <control> (SHIFT OUT through ESCAPE)
        // U+007F..U+009F: <control> (DELETE through APPLICATION PROGRAM COMMAND)
        if (codepoint <= 0x08 || (codepoint in 0x0E..0x1B) || (codepoint in 0x7F..0x9F)) {
            return true
        }
        // Also ignorable: Format characters (category Cf)
        val props = CharacterData.getProperties(codepoint)
        return getCategory(props) == CharacterData.CAT_CF
    }

    fun isUnicodeIdentifierStart(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_UNICODE_ID_START_BIT) != 0
    }

    fun isUnicodeIdentifierPart(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_UNICODE_ID_PART_BIT) != 0
    }

    fun isJavaIdentifierStart(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_JAVA_ID_START_BIT) != 0
    }

    fun isJavaIdentifierPart(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_JAVA_ID_PART_BIT) != 0
    }

    fun isISOControl(codepoint: Int): Boolean =
        // C0 control codes (U+0000..U+001F) and C1 control codes (U+007F..U+009F)
        codepoint in 0x00..0x1F || codepoint in 0x7F..0x9F

    fun getUnicodeScript(codepoint: Int): UnicodeScript = ScriptData.getScript(codepoint)
}
