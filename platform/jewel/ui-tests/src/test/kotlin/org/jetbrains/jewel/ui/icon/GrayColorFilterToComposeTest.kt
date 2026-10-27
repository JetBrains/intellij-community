// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.asSkiaColorFilter
import com.intellij.platform.icons.impl.filters.GrayColorFilter
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.junit.Test

class GrayColorFilterToComposeTest {
    private val light = GrayColorFilter(brightness = 33, contrast = -35, alpha = 100)
    private val dark = GrayColorFilter(brightness = -70, contrast = -70, alpha = 100)
    private val neutral = GrayColorFilter(brightness = 0, contrast = 0, alpha = 100)
    private val highContrast = GrayColorFilter(brightness = -20, contrast = 50, alpha = 100)
    private val translucent = GrayColorFilter(brightness = 0, contrast = 0, alpha = 50)

    private val opaqueColors =
        intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF808080.toInt(),
            0xFF3574F0.toInt(),
            0xFF5FB865.toInt(),
            0xFFDB5C5C.toInt(),
            0xFFEDA200.toInt(),
        )

    @Test
    fun `Light gray filter is within one level of the legacy filter for opaque colors`() {
        assertOpaqueColorsNearLegacy(light)
    }

    @Test
    fun `dark gray filter is within one level of the legacy filter for opaque colors`() {
        assertOpaqueColorsNearLegacy(dark)
    }

    @Test
    fun `gray filter without brightness and contrast is within one level of the legacy luminance`() {
        assertOpaqueColorsNearLegacy(neutral)
    }

    @Test
    fun `gray filter with a positive contrast is within one level of the legacy filter for some colors`() {
        val actual = filteredOpaque(highContrast, opaqueColors)
        for ([i, color] in opaqueColors.withIndex()) {
            assertWithin(legacy(highContrast, color), actual[i], tolerance = 1, "color %08x".format(color))
        }
    }

    @Test
    fun `gray filter with a positive contrast can differ by the full range at the jump`() {
        // The luminance is 126.89. GrayFilter truncates it to 126, which is below the jump at 127, so a contrast of
        // 100 gives black. Skia rounds it to 127, which is above the jump, so the same contrast gives white.
        val filter = GrayColorFilter(brightness = 0, contrast = 100, alpha = 100)
        val color = 0xFF7F7F7E.toInt()

        assertEquals(0x00, legacy(filter, color) and 0xFF)
        assertEquals(0xFF, filtered(filter, color) and 0xFF)
    }

    @Test
    fun `equal gray filters give the same Compose filter`() {
        val first = GrayColorFilter(brightness = 33, contrast = -35, alpha = 100).toCompose()
        val second = GrayColorFilter(brightness = 33, contrast = -35, alpha = 100).toCompose()

        assertSame(first, second)
    }

    @Test
    fun `different gray filters give different Compose filters`() {
        assertNotSame(light.toCompose(), dark.toCompose())
    }

    @Test
    fun `gray filter scales the alpha within one level of the legacy filter for every alpha value`() {
        for (filterAlpha in listOf(100, 50, 33, 10)) {
            val filter = GrayColorFilter(brightness = 0, contrast = 0, alpha = filterAlpha)
            for (alpha in 0..255) {
                val color = (alpha shl 24) or 0x3574F0
                val diff = abs((legacy(filter, color) ushr 24) - (filtered(filter, color) ushr 24))
                assertTrue(diff <= 1, "alpha $alpha of $filter is $diff levels from the legacy alpha")
            }
        }
    }

    @Test
    fun `gray filter keeps the gray of a translucent color`() {
        val color = 0x803574F0.toInt()
        assertWithin(legacy(translucent, color), filtered(translucent, color), tolerance = 2)
        assertWithin(legacy(light, color), filtered(light, color), tolerance = 2)
    }

    private fun assertOpaqueColorsNearLegacy(filter: GrayColorFilter) {
        val colors = opaqueColors + IntArray(SWEEP_SIZE) { 0xFF000000.toInt() or (it * SWEEP_STEP) }
        val actual = filteredOpaque(filter, colors)
        for ([i, color] in colors.withIndex()) {
            assertWithin(legacy(filter, color), actual[i], tolerance = 1, "color %08x".format(color))
        }
    }

    private fun filtered(filter: GrayColorFilter, argb: Int): Int {
        val bitmap = Bitmap().apply { allocN32Pixels(1, 1) }
        val paint =
            Paint().apply {
                color = argb
                blendMode = BlendMode.SRC
                colorFilter = filter.toCompose().asSkiaColorFilter()
            }
        Canvas(bitmap).use { it.drawPaint(paint) }
        return bitmap.getColor(0, 0)
    }

    private fun filteredOpaque(filter: GrayColorFilter, colors: IntArray): IntArray {
        require(colors.size <= SIDE * SIDE)
        val bytes = ByteArray(SIDE * SIDE * 4)
        for ([i, argb] in colors.withIndex()) {
            bytes[4 * i] = argb.toByte()
            bytes[4 * i + 1] = (argb shr 8).toByte()
            bytes[4 * i + 2] = (argb shr 16).toByte()
            bytes[4 * i + 3] = 0xFF.toByte()
        }
        val source =
            Image.makeRaster(ImageInfo(SIDE, SIDE, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, SIDE * 4)
        val info = ImageInfo(SIDE, SIDE, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap().apply { allocPixels(info) }
        val paint =
            Paint().apply {
                blendMode = BlendMode.SRC
                colorFilter = filter.toCompose().asSkiaColorFilter()
            }
        Canvas(bitmap).use { it.drawImage(source, 0f, 0f, paint) }
        val pixels = checkNotNull(bitmap.readPixels(info, SIDE * 4, 0, 0))
        return IntArray(colors.size) { i ->
            val blue = pixels[4 * i].toInt() and 0xFF
            val green = pixels[4 * i + 1].toInt() and 0xFF
            val red = pixels[4 * i + 2].toInt() and 0xFF
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    /** Copy of `com.intellij.util.ui.GrayFilter.filterRGB`, which the `ui` module cannot use. */
    private fun legacy(filter: GrayColorFilter, argb: Int): Int {
        val brightness = (filter.brightness.toDouble().pow(3) / (100f * 100f)).toFloat()
        val contrast = filter.contrast / 100f
        var gray = (0.30 * (argb shr 16 and 0xff) + 0.59 * (argb shr 8 and 0xff) + 0.11 * (argb and 0xff)).toInt()
        gray =
            if (brightness >= 0) ((gray + brightness * 255) / (1 + brightness)).toInt()
            else (gray / (1 - brightness)).toInt()
        gray =
            if (contrast >= 0) {
                if (gray >= 127) (gray + (255 - gray) * contrast).toInt() else (gray - gray * contrast).toInt()
            } else {
                (127 + (gray - 127) * (contrast + 1)).toInt()
            }
        val alpha = (argb ushr 24 and 0xff) * filter.alpha / 100
        return (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
    }

    private fun assertWithin(expected: Int, actual: Int, tolerance: Int, message: String = "") {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val diff = abs((expected ushr shift and 0xff) - (actual ushr shift and 0xff))
            assertTrue(diff <= tolerance, "$message: expected %08x but was %08x".format(expected, actual))
        }
    }

    private companion object {
        const val SIDE = 1024
        const val SWEEP_STEP = 17
        const val SWEEP_SIZE = 0x1000000 / SWEEP_STEP
    }
}
