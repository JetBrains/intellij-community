package fleet.codepoints.test

import fleet.codepoints.Category
import fleet.codepoints.Codepoint
import fleet.codepoints.codepoints
import fleet.codepoints.isDoubleWidthCharacter
import fleet.codepoints.isEmoji
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodepointTest {
    @Test
    fun isDoubleWidthTest() {
        val codePoints = "➜".codepoints().toList()
        assertEquals(1, codePoints.size)
        val codePoint = codePoints.single()
        assertFalse { isDoubleWidthCharacter(codePoint.codepoint) }
        assertFalse { isEmoji(codePoint.codepoint) }
    }

    @Test
    fun testGetCategory() {
        assertEquals(Category.UPPERCASE_LETTER, Codepoint('A'.code).getCategory())
        assertEquals(Category.LOWERCASE_LETTER, Codepoint('a'.code).getCategory())
        assertEquals(Category.TITLECASE_LETTER, Codepoint(0x01C5).getCategory()) // Dž
        assertEquals(Category.DECIMAL_DIGIT_NUMBER, Codepoint('0'.code).getCategory())
        assertEquals(Category.SPACE_SEPARATOR, Codepoint(' '.code).getCategory())
        assertEquals(Category.OTHER_PUNCTUATION, Codepoint('!'.code).getCategory())
        assertEquals(Category.MATH_SYMBOL, Codepoint('+'.code).getCategory())
        assertEquals(Category.CURRENCY_SYMBOL, Codepoint('$'.code).getCategory())
        assertEquals(Category.CONNECTOR_PUNCTUATION, Codepoint('_'.code).getCategory())
        assertEquals(Category.CONTROL, Codepoint(0x0009).getCategory())
        assertEquals(Category.FORMAT, Codepoint(0x200D).getCategory()) // zero-width joiner
        assertEquals(Category.OTHER_LETTER, Codepoint('漢'.code).getCategory())
        assertEquals(Category.NON_SPACING_MARK, Codepoint(0x0301).getCategory()) // combining acute accent
        assertEquals(Category.SURROGATE, Codepoint(0xD800).getCategory())
        assertEquals(Category.PRIVATE_USE, Codepoint(0xE000).getCategory())

        // Supplementary plane: mathematical bold capital A
        assertEquals(Category.UPPERCASE_LETTER, Codepoint(0x1D400).getCategory())

        // Unassigned codepoint
        assertEquals(Category.UNASSIGNED, Codepoint(0x10FFFF).getCategory())

        assertEquals("Lu", Codepoint('A'.code).getCategory().code)
    }
}