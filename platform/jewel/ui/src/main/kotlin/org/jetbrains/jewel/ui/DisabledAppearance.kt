// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.pow
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.LocalDisabledAppearanceValues
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * A Modifier that applies a visual 'disabled' effect to a Composable by adjusting its brightness, contrast, and alpha.
 *
 * This modifier is a high-performance alternative to the traditional `ColorFilter` for creating a disabled look. It
 * uses a custom SKSL fragment shader (via Skia's `RuntimeEffect` and `ImageFilter`) to apply non-linear transformations
 * to the rendered output.
 *
 * The underlying mechanism works by drawing the composable's content into a separate graphics layer, applying the
 * complex filter to that layer, and then drawing the result back to the canvas. This is more powerful than a simple
 * `ColorMatrix` and correctly replicates the appearance of disabled components in IntelliJ platform themes.
 *
 * ### Usage
 *
 * This modifier should be applied conditionally to convey a disabled state.
 *
 * ```
 * val isEnabled = false
 * Button(
 *     onClick = { /* ... */ },
 *     enabled = isEnabled,
 *     modifier = Modifier.thenIf(!isEnabled) { disabledAppearance() }
 * ) {
 *     Text("Click Me")
 * }
 * ```
 *
 * @param brightness The brightness adjustment, in a range of -100 to 100. A value of 0 has no effect. Positive values
 *   increase brightness, negative values decrease it. The default is provided by `LocalGrayFilterValues.current`.
 * @param contrast The contrast adjustment, in a range of -100 to 100. A value of 0 has no effect. Positive values
 *   increase contrast, negative values decrease it. The default is provided by `LocalGrayFilterValues.current`.
 * @param alpha The final alpha multiplier, in a range of 0 to 100. A value of 100 means the original alpha is
 *   unchanged, while 0 would render the content fully transparent. The default is provided by
 *   `LocalGrayFilterValues.current`.
 * @return A [Modifier] instance that applies the disabled effect.
 * @see LocalDisabledAppearanceValues
 */
@Composable
public fun Modifier.disabledAppearance(
    brightness: Int = LocalDisabledAppearanceValues.current.brightness,
    contrast: Int = LocalDisabledAppearanceValues.current.contrast,
    alpha: Int = LocalDisabledAppearanceValues.current.alpha,
): Modifier {
    val effect = remember { RuntimeEffect.makeForShader(GRAY_FILTER_SKSL_FOR_IMAGEFILTER) }
    val paint = remember { SkiaPaint() }

    return drawWithContent {
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
