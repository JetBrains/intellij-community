package org.jetbrains.jewel.markdown.styling

import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles.NumberFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

public class OrderedListNumberFormatStylesTest {
    @Test
    public fun `numberFormatStyles should use firstLevel as default for others`() {
        val styles = MarkdownStyling.List.Ordered.NumberFormatStyles(firstLevel = NumberFormatStyle.Decimal)

        assertEquals(styles.secondLevel, styles.firstLevel)
        assertEquals(styles.thirdLevel, styles.secondLevel)
    }

    @Test
    public fun `decimal format should return standard number string`() {
        val result = NumberFormatStyle.Decimal.formatNumber(42)
        assertEquals("42", result)
    }

    @Test
    public fun `decimal format should throw exception for zero value`() {
        val style = NumberFormatStyle.Decimal

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(0) }
    }

    @Test
    public fun `decimal format should throw exception for negative value`() {
        val style = NumberFormatStyle.Decimal

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(-5) }
    }

    @Test
    public fun `roman format should return correct Roman numeral`() {
        val style = NumberFormatStyle.Roman

        assertEquals("i", style.formatNumber(1))
        assertEquals("iv", style.formatNumber(4))
        assertEquals("v", style.formatNumber(5))
        assertEquals("ix", style.formatNumber(9))
        assertEquals("x", style.formatNumber(10))
        assertEquals("xl", style.formatNumber(40))
        assertEquals("l", style.formatNumber(50))
        assertEquals("xc", style.formatNumber(90))
        assertEquals("c", style.formatNumber(100))
        assertEquals("cd", style.formatNumber(400))
        assertEquals("d", style.formatNumber(500))
        assertEquals("cm", style.formatNumber(900))
        assertEquals("m", style.formatNumber(1000))
        assertEquals("mmmcmxcix", style.formatNumber(3999))
    }

    @Test
    public fun `roman format should throw exception for zero value`() {
        val style = NumberFormatStyle.Roman

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(0) }
    }

    @Test
    public fun `roman format should throw exception on non-positive numbers`() {
        val style = NumberFormatStyle.Roman

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(-5) }
    }

    @Test
    public fun `alphabetical format should convert to base-26 string`() {
        val style = NumberFormatStyle.Alphabetical

        assertEquals("a", style.formatNumber(1))
        assertEquals("z", style.formatNumber(26))
        assertEquals("aa", style.formatNumber(27))
        assertEquals("ab", style.formatNumber(28))
        assertEquals("az", style.formatNumber(52))
        assertEquals("ba", style.formatNumber(53))
        assertEquals("zz", style.formatNumber(702))
        assertEquals("aaa", style.formatNumber(703))
    }

    @Test
    public fun `alphabetical format should throw exception for zero value`() {
        val style = NumberFormatStyle.Alphabetical

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(0) }
    }

    @Test
    public fun `alphabetical format should throw exception on non-positive numbers`() {
        val style = NumberFormatStyle.Alphabetical

        assertThrows(IllegalArgumentException::class.java) { style.formatNumber(-5) }
    }
}
