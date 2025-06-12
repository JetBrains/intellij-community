package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.pow
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.LocalGrayFilterValues
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Implements javax.swing.GrayFilter's behaviour with percent = 50, brighter = true
// to match the GrayFilter#createDisabledImage behavior, used by Swing.
private val disabledColorMatrixGammaEncoded =
    ColorMatrix().apply {
        val saturation = .5f

        // We use NTSC luminance weights like Swing does as it's gamma-encoded RGB,
        // and add some brightness to emulate Swing's "brighter" approach, which is
        // not representable with a ColorMatrix alone as it's a non-linear op.
        val redFactor = .299f * saturation + .25f
        val greenFactor = .587f * saturation + .25f
        val blueFactor = .114f * saturation + .25f
        this[0, 0] = redFactor
        this[0, 1] = greenFactor
        this[0, 2] = blueFactor
        this[1, 0] = redFactor
        this[1, 1] = greenFactor
        this[1, 2] = blueFactor
        this[2, 0] = redFactor
        this[2, 1] = greenFactor
        this[2, 2] = blueFactor
    }

@Deprecated("Use Modifier.grayFilter() instead to get correct behaviour in dark themes too.")
public fun ColorFilter.Companion.disabled(): ColorFilter = colorMatrix(disabledColorMatrixGammaEncoded)

@Composable
public fun Modifier.grayFilter(): Modifier {
    val grayFilterValues = LocalGrayFilterValues.current

    val (brightness, contrast, alpha) =
        arrayOf(grayFilterValues.brightness, grayFilterValues.contrast, grayFilterValues.alpha)

    require(brightness in -100..100) { "The brightness must be in [-100, 100], but was $brightness" }
    require(contrast in -100..100) { "The contrast must be in [-100, 100], but was $contrast" }
    require(alpha in 0..100) { "The alpha must be in [0, 100], but was $alpha" }

    val effect = remember { RuntimeEffect.makeForShader(GRAY_FILTER_SKSL_FOR_IMAGEFILTER) }
    val paint = remember { SkiaPaint() }

    return this.drawWithContent {
        drawIntoCanvas { canvas ->
            // Create a builder and set uniforms inside the draw scope.
            val builder =
                RuntimeShaderBuilder(effect).apply {
                    val brightnessFloat = (brightness.toDouble().pow(3) / (100.0 * 100.0)).toFloat()
                    val contrastFloat = contrast / 100f
                    val alphaScale = alpha / 100f

                    uniform("brightness", brightnessFloat)
                    uniform("contrast", contrastFloat)
                    uniform("alphaScale", alphaScale)
                }

            // Create the ImageFilter from the shader and apply it to the native Skia paint.
            paint.imageFilter = ImageFilter.makeRuntimeShader(builder, "content", null)

            // Use the NATIVE canvas to save a layer with the NATIVE paint.
            val skiaRect = SkiaRect.makeWH(size.width, size.height)
            canvas.nativeCanvas.saveLayer(skiaRect, paint)

            // Draw the original composable content into the layer.
            drawContent()

            // Restore the native canvas to apply the filter.
            canvas.nativeCanvas.restore()
        }
    }
}

@Language("GLSL")
private const val GRAY_FILTER_SKSL_FOR_IMAGEFILTER =
    """
    uniform shader content;
    uniform float brightness;
    uniform float contrast;
    uniform float alphaScale;

    vec4 main(vec2 fragCoord) {
        vec4 color = content.eval(fragCoord);
        if (color.a > 0) {
            color.rgb /= color.a;
        }

        float gray = dot(color.rgb, vec3(0.30, 0.59, 0.11));

        if (brightness >= 0) {
            gray = (gray + brightness) / (1.0 + brightness);
        } else {
            gray = gray / (1.0 - brightness);
        }

        float midGray = 0.5;
        if (contrast >= 0) {
            if (gray >= midGray) {
                gray = gray + (1.0 - gray) * contrast;
            } else {
                gray = gray - gray * contrast;
            }
        } else {
            gray = midGray + (gray - midGray) * (contrast + 1.0);
        }

        gray = clamp(gray, 0.0, 1.0);

        float newAlpha = color.a * alphaScale;
        return vec4(vec3(gray) * newAlpha, newAlpha);
    }
"""
