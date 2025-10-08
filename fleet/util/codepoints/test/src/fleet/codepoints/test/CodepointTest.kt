package fleet.codepoints.test

import fleet.codepoints.isDoubleWidthCharacter
import fleet.codepoints.isEmoji
import kotlin.test.Test
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodepointTest {
    @Test
    fun isDoubleWidthTest() {
        val codePoints = "➜".codePoints().toList()
        assertEquals(1, codePoints.size)
        val codePoint = codePoints.single()
        assertFalse { isDoubleWidthCharacter(codePoint) }
        assertFalse { isEmoji(codePoint) }
    }
}