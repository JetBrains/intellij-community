@file:Suppress("RedundantSuppression", "DEPRECATION")

package org.jetbrains.jewel.ui.component

import androidx.annotation.Px
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isUnspecified
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** Returns the sum of this [TextUnit] and [other], provided they share the same unit type. */
public operator fun TextUnit.plus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value + other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value + other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value + other.value, TextUnitType.Unspecified)
        else -> error("Can't add together different TextUnits. Got $type and ${other.type}")
    }

/** Returns the difference between this [TextUnit] and [other], provided they share the same unit type. */
public operator fun TextUnit.minus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value - other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value - other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value - other.value, TextUnitType.Unspecified)
        else -> error("Can't subtract different TextUnits. Got $type and ${other.type}")
    }

@Suppress("UndesirableClassUsage") // We're not in the IJP in this module (suppression only needed for JPS)
private val image = BufferedImage(1, 1, TYPE_INT_ARGB)

/**
 * Computes the font's "base" line height with the same logic used by
 * [com.intellij.openapi.editor.impl.view.EditorView.initMetricsIfNeeded].
 *
 * This is useful to set a valid [TextStyle.lineHeight] when trying to match the metrics used by Swing and the IJP.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Px
public fun Font.computeLineHeightPx(): Int {
    // We need to create a Graphics2D to get its FontRendererContext. The only way we have is
    // by requesting a BufferedImage to create one for us. We can't reuse it because the FRC
    // instance is cached inside the Graphics2D and may lead to incorrect scales being applied.
    val graphics2D = image.createGraphics()

    // The logic below emulates the JBR API's FontMetricsAccessor_fallback logic to set up the
    // measurements. Ideally, we'd use JBR.getFontMetricsAccessor(), like IJP does, but that
    // crashes at runtime in standalone.
    val context = getDefaultFrc()
    with(graphics2D) {
        transform = context.transform
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, context.antiAliasingHint)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, context.fractionalMetricsHint)
    }
    val metrics = graphics2D.getFontMetrics(this)
    return metrics.height
}

private var defaultFrc: FontRenderContext? = null

private fun getDefaultFrc(): FontRenderContext {
    // This code is lifted from the JBR's FontDesignMetrics
    if (defaultFrc == null) {
        val tx =
            if (GraphicsEnvironment.isHeadless()) {
                AffineTransform()
            } else {
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                    .defaultConfiguration
                    .defaultTransform
            }
        defaultFrc = FontRenderContext(tx, false, false)
    }
    return checkNotNull(defaultFrc) { "The defaultFrc should never be null" }
}
