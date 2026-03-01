package fleet.codepoints.test

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
}