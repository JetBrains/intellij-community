package org.jetbrains.jewel.markdown.styling

import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.junit.Assert.assertEquals
import org.junit.Test

public class UnorderedListBulletCharStylesTest {
    @Test
    public fun `bulletCharStyles should use firstLevel as default for others`() {
        val styles = MarkdownStyling.List.Unordered.BulletCharStyles(firstLevel = '•')

        assertEquals('•', styles.firstLevel)
        assertEquals('•', styles.secondLevel)
        assertEquals('•', styles.thirdLevel)
    }

    @Test
    public fun `bulletCharStyles should use the respective format level as declared`() {
        val styles =
            MarkdownStyling.List.Unordered.BulletCharStyles(firstLevel = 'o', secondLevel = 'm', thirdLevel = 'g')

        assertEquals('o', styles.firstLevel)
        assertEquals('m', styles.secondLevel)
        assertEquals('g', styles.thirdLevel)
    }
}
