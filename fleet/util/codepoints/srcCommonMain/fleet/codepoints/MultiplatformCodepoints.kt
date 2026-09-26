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

    private const val ASCII_SPACE = 0x20

    // Per-codepoint ASCII property flags packed into one shared 128-entry table,
    // so every ASCII fast path is a single load and mask test. Chains of range
    // checks are data-dependent and mispredict on mixed ASCII input.
    private const val AP_LETTER = 1 shl 0
    private const val AP_DIGIT = 1 shl 1
    private const val AP_UPPER = 1 shl 2
    private const val AP_LOWER = 1 shl 3
    private const val AP_WHITESPACE = 1 shl 4
    private const val AP_ID_IGNORABLE = 1 shl 5
    private const val AP_UNICODE_ID_PART = 1 shl 6
    private const val AP_JAVA_ID_START = 1 shl 7
    private const val AP_JAVA_ID_PART = 1 shl 8

    private val ASCII_PROPS = IntArray(0x80) { cp ->
        var props = 0
        if (cp in 'A'.code..'Z'.code) props = props or AP_LETTER or AP_UPPER
        if (cp in 'a'.code..'z'.code) props = props or AP_LETTER or AP_LOWER
        if (cp in '0'.code..'9'.code) props = props or AP_DIGIT
        if (cp == ASCII_SPACE || cp in 0x09..0x0D) props = props or AP_WHITESPACE
        if (cp in 0x00..0x08 || cp in 0x0E..0x1B || cp == 0x7F) props = props or AP_ID_IGNORABLE
        if (props and (AP_LETTER or AP_DIGIT) != 0 || cp == '_'.code) props = props or AP_UNICODE_ID_PART
        if (props and AP_LETTER != 0 || cp == '$'.code || cp == '_'.code) props = props or AP_JAVA_ID_START
        if (props and (AP_UNICODE_ID_PART or AP_ID_IGNORABLE) != 0 || cp == '$'.code) props = props or AP_JAVA_ID_PART
        props
    }

    // Callers must guard with isAscii
    private fun hasAsciiProp(codepoint: Int, flag: Int): Boolean =
        ASCII_PROPS[codepoint] and flag != 0

    // Helper functions for property interpretation
    private fun getCategoryCode(props: Int): Int =
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

    private fun getAsciiUnicodeScript(codepoint: Int): UnicodeScript =
        if (hasAsciiProp(codepoint, AP_LETTER)) UnicodeScript.LATIN else UnicodeScript.COMMON

    private fun isLetterSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_LETTER_BIT) != 0
    }

    private fun isDigitSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_DIGIT_BIT) != 0
    }

    private fun isLetterOrDigitSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and (CharacterData.IS_LETTER_BIT or CharacterData.IS_DIGIT_BIT)) != 0
    }

    private fun isUpperCaseSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_UPPERCASE_BIT) != 0
    }

    private fun isLowerCaseSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_LOWERCASE_BIT) != 0
    }

    private fun toLowerCaseSlow(codepoint: Int): Int {
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

    private fun toUpperCaseSlow(codepoint: Int): Int {
        val props = CharacterData.getProperties(codepoint)

        // Special handling for titlecase letters (Lt) - these map to their uppercase variants
        // U+01C5 Dž -> U+01C4 DŽ, U+01C8 Lj -> U+01C7 LJ
        // U+01CB Nj -> U+01CA NJ, U+01F2 Dz -> U+01F1 DZ
        if (getCategoryCode(props) == CharacterData.CAT_LT) {
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

    private fun isSpaceCharSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_SPACE_CHAR_BIT) != 0
    }

    private fun isWhitespaceSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_WHITESPACE_BIT) != 0
    }

    private fun isIdeographicSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_IDEOGRAPHIC_BIT) != 0
    }

    private fun isIdentifierIgnorableSlow(codepoint: Int): Boolean {
        if (codepoint in 0x7F..0x9F) {
            return true
        }
        // Also ignorable: Format characters (category Cf)
        val props = CharacterData.getProperties(codepoint)
        return getCategoryCode(props) == CharacterData.CAT_CF
    }

    private fun isUnicodeIdentifierStartSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_UNICODE_ID_START_BIT) != 0
    }

    private fun isUnicodeIdentifierPartSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_UNICODE_ID_PART_BIT) != 0
    }

    private fun isJavaIdentifierStartSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_JAVA_ID_START_BIT) != 0
    }

    private fun isJavaIdentifierPartSlow(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return (props and CharacterData.IS_JAVA_ID_PART_BIT) != 0
    }

    fun codepointOf(highSurrogate: Char, lowSurrogate: Char): Codepoint =
        Codepoint((highSurrogate.code shl 10) + lowSurrogate.code + SURROGATE_DECODE_OFFSET)

    fun highSurrogate(codepoint: Int): Char = ((codepoint ushr 10) + HIGH_SURROGATE_ENCODE_OFFSET).toChar()
    fun lowSurrogate(codepoint: Int): Char = ((codepoint and 0x3FF) + MIN_LOW_SURROGATE).toChar()

    fun isLetter(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_LETTER) else isLetterSlow(codepoint)

    fun isDigit(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_DIGIT) else isDigitSlow(codepoint)

    fun isLetterOrDigit(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_LETTER or AP_DIGIT) else isLetterOrDigitSlow(codepoint)

    fun isUpperCase(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_UPPER) else isUpperCaseSlow(codepoint)

    fun isLowerCase(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_LOWER) else isLowerCaseSlow(codepoint)

    fun toLowerCase(codepoint: Int): Int =
        if (isAscii(codepoint)) asciiToLowerCase(codepoint) else toLowerCaseSlow(codepoint)

    fun toUpperCase(codepoint: Int): Int =
        if (isAscii(codepoint)) asciiToUpperCase(codepoint) else toUpperCaseSlow(codepoint)

    fun isSpaceChar(codepoint: Int): Boolean =
        if (isAscii(codepoint)) codepoint == ASCII_SPACE else isSpaceCharSlow(codepoint)

    fun isWhitespace(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_WHITESPACE) else isWhitespaceSlow(codepoint)

    fun isIdeographic(codepoint: Int): Boolean =
        if (isAscii(codepoint)) false else isIdeographicSlow(codepoint)

    fun isIdentifierIgnorable(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_ID_IGNORABLE) else isIdentifierIgnorableSlow(codepoint)

    fun isUnicodeIdentifierStart(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_LETTER) else isUnicodeIdentifierStartSlow(codepoint)

    fun isUnicodeIdentifierPart(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_UNICODE_ID_PART) else isUnicodeIdentifierPartSlow(codepoint)

    fun isJavaIdentifierStart(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_JAVA_ID_START) else isJavaIdentifierStartSlow(codepoint)

    fun isJavaIdentifierPart(codepoint: Int): Boolean =
        if (isAscii(codepoint)) hasAsciiProp(codepoint, AP_JAVA_ID_PART) else isJavaIdentifierPartSlow(codepoint)

    fun isISOControl(codepoint: Int): Boolean =
        // C0 control codes (U+0000..U+001F) and C1 control codes (U+007F..U+009F).
        // Each range collapses to a single unsigned comparison via the standard
        // "(x - low) <= (high - low), unsigned" range-check idiom, turning four
        // signed comparisons into two unsigned ones. An out-of-range codepoint
        // underflows to a large unsigned value and fails the bound.
        // https://www.geeksforgeeks.org/cpp/how-to-check-whether-a-number-is-in-the-rangea-b-using-one-comparison/
        codepoint.toUInt() <= 0x1Fu || (codepoint - 0x7F).toUInt() <= 0x20u

    fun isPrivateUse(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return getCategoryCode(props) == CharacterData.CAT_CO
    }

    fun isDefined(codepoint: Int): Boolean {
        val props = CharacterData.getProperties(codepoint)
        return getCategoryCode(props) != CharacterData.CAT_CN
    }

    fun getUnicodeScript(codepoint: Int): UnicodeScript =
        if (isAscii(codepoint)) getAsciiUnicodeScript(codepoint) else ScriptData.getScript(codepoint)

    fun getCategory(codepoint: Int): Category =
        // Category.ordinal matches the category codes packed into CharacterData.
        Category.entries[getCategoryCode(CharacterData.getProperties(codepoint))]
}
