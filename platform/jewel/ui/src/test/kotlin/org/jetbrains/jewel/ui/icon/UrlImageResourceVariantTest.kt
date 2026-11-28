// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.geometry.Size
import com.intellij.platform.icons.impl.FileImageResourceLocation
import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ImageModifiers
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Images at a URL (e.g. a custom action icon file) in Compose: the variant for the theme and stroke is picked like for
 * class path images ([PathImageResourceLocation]), and a file that cannot be read draws nothing instead of failing.
 *
 * Each file has its own size, which tells which of them was read.
 */
internal class UrlImageResourceVariantTest {
    @get:Rule val folder = TemporaryFolder()

    private val provider = ComposeImageResourceProvider()

    @Test
    fun `a dark theme reads the dark variant`() {
        val base = svg("custom.svg", 16)
        svg("custom_dark.svg", 20)

        assertEquals(Size(20f, 20f), sizeOf(base, DefaultImageModifiers(isDark = true)))
        assertEquals(Size(16f, 16f), sizeOf(base, DefaultImageModifiers(isDark = false)))
    }

    @Test
    fun `a dark theme reads the base file when there is no dark variant`() {
        val base = svg("custom.svg", 16)

        assertEquals(Size(16f, 16f), sizeOf(base, DefaultImageModifiers(isDark = true)))
    }

    @Test
    fun `a stroked image reads the stroke variant, and the light base file without one`() {
        // (the stroke recoloring itself needs an IconManager, so this checks which file is read)
        val withVariant = svg("run.svg", 16)
        val strokeVariant = svg("run_stroke.svg", 24)
        svg("run_dark.svg", 20)
        val withoutVariant = svg("plain.svg", 16)
        svg("plain_dark.svg", 20)
        val stroked = DefaultImageModifiers(isDark = true, stroke = DefaultSRGB.fromHex("#FF0000"))

        val fromVariant = resolveUrlImage(withVariant.toURI().toString(), stroked)!!
        assertTrue(fromVariant.isAuthoredStrokeVariant)
        assertEquals(strokeVariant.readText(), fromVariant.data.decodeToString())

        val fromBase = resolveUrlImage(withoutVariant.toURI().toString(), stroked)!!
        assertFalse(fromBase.isAuthoredStrokeVariant)
        assertEquals(withoutVariant.readText(), fromBase.data.decodeToString())
    }

    @Test
    fun `a file in a folder with a space in its name is read`() {
        val inFolderWithSpace = File(folder.newFolder("with space"), "custom.svg")
        inFolderWithSpace.writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18"><rect width="18" height="18" fill="#6C707E"/></svg>"""
        )

        assertEquals(Size(18f, 18f), sizeOf(inFolderWithSpace, DefaultImageModifiers()))
    }

    @Test
    fun `an image file location is read from its path`() {
        val file = svg("custom.svg", 16)

        val resource = provider.loadImage(FileImageResourceLocation(file.path), DefaultImageModifiers())

        assertEquals(Size(16f, 16f), (resource as ComposePainterImageResource).painter.intrinsicSize)
    }

    @Test
    fun `a file that cannot be read draws nothing`() {
        val missing = File(folder.root, "missing.svg")

        val resource = provider.loadImage(UrlImageResourceLocation(missing.toURI().toString()), DefaultImageModifiers())

        assertEquals(Size.Zero, (resource as ComposePainterImageResource).painter.intrinsicSize)
    }

    @Test
    fun `a file that cannot be decoded draws nothing`() {
        // e.g. a custom icon file that the user chose by mistake
        val corrupt = File(folder.root, "corrupt.svg").apply { writeText("not an SVG image") }

        val resource = provider.loadImage(UrlImageResourceLocation(corrupt.toURI().toString()), DefaultImageModifiers())

        assertEquals(Size.Zero, (resource as ComposePainterImageResource).painter.intrinsicSize)
    }

    @Test
    fun `the format of an image is taken from the path of its URL, without the fragment`() {
        val file = svg("custom.svg", 16)

        val resource =
            provider.loadImage(UrlImageResourceLocation(file.toURI().toString() + "#icon"), DefaultImageModifiers())

        assertEquals(Size(16f, 16f), (resource as ComposePainterImageResource).painter.intrinsicSize)
    }

    @Test
    fun `a dark theme reads the dark variant also when the fragment of the URL has a dot`() {
        val base = svg("run.svg", 16)
        svg("run_dark.svg", 20)

        val resource =
            provider.loadImage(
                UrlImageResourceLocation(base.toURI().toString() + "#v2.0"),
                DefaultImageModifiers(isDark = true),
            )

        assertEquals(Size(20f, 20f), (resource as ComposePainterImageResource).painter.intrinsicSize)
    }

    @Test
    fun `an image at a URL has no size variants, as legacy icons`() {
        val base = svg("custom.svg", 16)
        svg("custom@20x20.svg", 20)
        val resource =
            provider.loadImage(
                UrlImageResourceLocation(base.toURI().toString()),
                DefaultImageModifiers(sizeVariantPolicy = SizeVariantPolicy.Fitting),
            ) as ComposePainterImageResource

        // 16 * 1.25 = 20 at density 2: a class loader resource takes its @20x20 variant here
        assertEquals(Size(16f, 16f), resource.painterFor(40, 40, DefaultScalingContext(2f, 1.25f)).intrinsicSize)
    }

    private fun svg(name: String, size: Int): File =
        File(folder.root, name).apply {
            writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size"><rect width="$size" height="$size" fill="#6C707E"/></svg>"""
            )
        }

    private fun sizeOf(file: File, modifiers: ImageModifiers): Size {
        val resource = provider.loadImage(UrlImageResourceLocation(file.toURI().toString()), modifiers)
        return (resource as ComposePainterImageResource).painter.intrinsicSize
    }
}
