// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.geometry.Size
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which file a stroked icon is read from.
 *
 * An icon may ship a hand-authored stroke variant: a separate drawing of the same glyph, already reduced to an outline.
 * Recoloring the base drawing is not a substitute for it — `strokeVariant.svg` is a filled shape inside an outline
 * whose color is outside the stroke palette, so no palette substitution turns it into the outlined glyph that
 * `strokeVariant_stroke.svg` holds. Icons that ship no such variant have to keep resolving to their base file.
 */
internal class PathImageResourceLocationTest {
    @Test
    fun `a stroked icon resolves its hand-authored stroke variant`() {
        val resolved = location("icons/strokeVariant.svg").resolve(stroked = true, isDark = false)

        assertTrue(resolved.isAuthoredStrokeVariant)
        assertEquals(contentOf("icons/strokeVariant_stroke.svg"), resolved.data.decodeToString())
    }

    @Test
    fun `a stroked icon without a variant falls back to its base file`() {
        val resolved = location("icons/search.svg").resolve(stroked = true, isDark = false)

        assertFalse(resolved.isAuthoredStrokeVariant)
        assertEquals(contentOf("icons/search.svg"), resolved.data.decodeToString())
    }

    @Test
    fun `a stroked icon takes light artwork in a dark theme`() {
        // The stroke palette describes the light variants, so a stroked icon resolves the variant rather than `_dark`
        // — and falls back to the light base file, not the dark one, when it ships no variant.
        assertTrue(location("icons/strokeVariant.svg").resolve(stroked = true, isDark = true).isAuthoredStrokeVariant)
        assertEquals(
            contentOf("icons/search.svg"),
            location("icons/search.svg").resolve(stroked = true, isDark = true).data.decodeToString(),
        )
    }

    @Test
    fun `a dark icon that is not stroked still resolves its dark variant`() {
        val resolved = location("icons/search.svg").resolve(stroked = false, isDark = true)

        assertFalse(resolved.isAuthoredStrokeVariant)
        assertEquals(contentOf("icons/search_dark.svg"), resolved.data.decodeToString())
    }

    @Test
    fun `an icon that is not stroked never resolves the variant`() {
        val resolved = location("icons/strokeVariant.svg").resolve(stroked = false, isDark = false)

        assertFalse(resolved.isAuthoredStrokeVariant)
        assertEquals(contentOf("icons/strokeVariant.svg"), resolved.data.decodeToString())
    }

    @Test
    fun `a dark size variant without its own dark file takes the light file`() {
        // `lightSizeVariant@20x20.svg` has no `_dark` file, as in the legacy icons, which then draw the light file
        val resource =
            ComposeImageResourceProvider()
                .loadImage(location("icons/lightSizeVariant.svg"), DefaultImageModifiers(isDark = true))
                as ComposePainterImageResource

        // 16 * 1.25 = 20 at density 2
        assertEquals(Size(20f, 20f), resource.painterFor(40, 40, DefaultScalingContext(2f, 1.25f)).intrinsicSize)
    }

    @Test
    fun `a dark size variant reads its own dark file`() {
        val resolved = location("icons/search@20x20.svg").resolveOrBase(DefaultImageModifiers(isDark = true))

        assertEquals(contentOf("icons/search@20x20_dark.svg"), resolved.data.decodeToString())
    }

    private fun location(path: String) = PathImageResourceLocation(path, javaClass.classLoader)

    private fun contentOf(path: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "missing test resource: $path" }
            .use { it.readBytes() }
            .decodeToString()
}
