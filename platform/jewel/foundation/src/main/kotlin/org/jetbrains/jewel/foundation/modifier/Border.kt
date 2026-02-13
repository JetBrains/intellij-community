package org.jetbrains.jewel.foundation.modifier

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.min
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.grow
import org.jetbrains.jewel.foundation.hasAtLeastOneNonRoundedCorner
import org.jetbrains.jewel.foundation.shrink

public typealias DrawScopeStroke = androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Modifies the element to add a border defined by a [Stroke] object.
 *
 * This convenience method allows applying different types of borders (Solid, Brush, or None) dynamically based on the
 * provided [Stroke] configuration.
 *
 * @param stroke The definition of the stroke, which determines the border's width, alignment, and visual style.
 * @param shape The [Shape] of the border.
 */
public fun Modifier.border(stroke: Stroke, shape: Shape): Modifier =
    when (stroke) {
        is Stroke.None -> this
        is Stroke.Solid -> border(stroke.alignment, stroke.width, stroke.color, shape, stroke.expand)
        is Stroke.Brush ->
            border(
                alignment = stroke.alignment,
                width = stroke.width,
                brush = stroke.brush,
                shape = shape,
                expand = stroke.expand,
            )
    }

/**
 * Modifies the element to add a solid color border with specific alignment and expansion.
 *
 * @param alignment Determines where the border is drawn relative to the bounds: [Stroke.Alignment.Inside],
 *   [Stroke.Alignment.Center], or [Stroke.Alignment.Outside].
 * @param width The thickness of the border.
 * @param color The [Color] to fill the border with.
 * @param shape The [Shape] of the border. Defaults to [RectangleShape].
 * @param expand An optional value to expand (or shrink) the border's path relative to the content bounds. Defaults to
 *   [Dp.Unspecified], which applies no expansion.
 */
public fun Modifier.border(
    alignment: Stroke.Alignment,
    width: Dp,
    color: Color,
    shape: Shape = RectangleShape,
    expand: Dp = Dp.Unspecified,
): Modifier = border(alignment, width, SolidColor(color), shape, expand)

/**
 * Modifies the element to add a border using a [Brush] with specific alignment and expansion.
 *
 * **Optimization Note:** If [alignment] is [Stroke.Alignment.Inside] and [expand] is [Dp.Unspecified], this modifier
 * delegates to the native Jetpack Compose [androidx.compose.foundation.border]. This provides the most native
 * experience and performance for standard inside borders. In all other cases (Center/Outside alignment or custom
 * expansion), a custom drawing modifier is used.
 *
 * @param alignment Determines where the border is drawn relative to the bounds: [Stroke.Alignment.Inside],
 *   [Stroke.Alignment.Center], or [Stroke.Alignment.Outside].
 * @param width The thickness of the border.
 * @param brush The [Brush] to fill the border with (e.g., a Gradient).
 * @param shape The [Shape] of the border. Defaults to [RectangleShape].
 * @param expand An optional value to expand (or shrink) the border's path relative to the content bounds. Defaults to
 *   [Dp.Unspecified], which applies no expansion.
 */
public fun Modifier.border(
    alignment: Stroke.Alignment,
    width: Dp,
    brush: Brush,
    shape: Shape = RectangleShape,
    expand: Dp = Dp.Unspecified,
): Modifier =
    if (alignment == Stroke.Alignment.Inside && expand.isUnspecified) {
        // The compose native border modifier(androidx.compose.foundation.border) draws the border
        // inside the shape,
        // so we can just use that for getting a more native experience when drawing inside borders
        border(width, brush, shape)
    } else {
        drawBorderWithAlignment(alignment, width, brush, shape, expand)
    }

private fun Modifier.drawBorderWithAlignment(
    alignment: Stroke.Alignment,
    width: Dp,
    brush: Brush,
    shape: Shape,
    expand: Dp,
): Modifier = this then BorderWithAlignmentModifier(alignment, width, brush, shape, expand)

@Immutable
private data class BorderWithAlignmentModifier(
    val alignment: Stroke.Alignment,
    val width: Dp,
    val brush: Brush,
    val shape: Shape,
    val expand: Dp,
) : ModifierNodeElement<BorderWithAlignmentNode>() {
    override fun create() = BorderWithAlignmentNode(alignment, width, brush, shape, expand)

    override fun update(node: BorderWithAlignmentNode) {
        node.alignment = alignment
        node.width = width
        node.brush = brush
        node.shape = shape
        node.expand = expand
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBorderWithAlignment"
        properties["alignment"] = alignment
        properties["width"] = width
        if (brush is SolidColor) {
            properties["color"] = brush.value
            value = brush.value
        } else {
            properties["brush"] = brush
        }
        properties["shape"] = shape
        properties["expand"] = expand
    }
}

private class BorderWithAlignmentNode(
    alignmentParameter: Stroke.Alignment,
    widthParameter: Dp,
    brushParameter: Brush,
    shapeParameter: Shape,
    expandParameter: Dp,
) : DelegatingNode(), SemanticsModifierNode {
    override val shouldAutoInvalidate: Boolean = false
    override val isImportantForBounds = false

    val borderCache = BorderCache()

    var alignment = alignmentParameter
        set(value) {
            if (field != value) {
                field = value
                drawWithCacheModifierNode.invalidateDrawCache()
            }
        }

    var width = widthParameter
        set(value) {
            if (field != value) {
                field = value
                drawWithCacheModifierNode.invalidateDrawCache()
            }
        }

    var brush = brushParameter
        set(value) {
            if (field != value) {
                field = value
                drawWithCacheModifierNode.invalidateDrawCache()
            }
        }

    var shape = shapeParameter
        set(value) {
            if (field != value) {
                field = value
                drawWithCacheModifierNode.invalidateDrawCache()
            }
        }

    var expand = expandParameter
        set(value) {
            if (field != value) {
                field = value
                drawWithCacheModifierNode.invalidateDrawCache()
            }
        }

    private val drawWithCacheModifierNode =
        delegate(
            CacheDrawModifierNode {
                onDrawWithContent {
                    drawContent()

                    val strokeWidthPx =
                        min(if (width == Dp.Hairline) 1f else width.toPx(), size.minDimension / 2).coerceAtLeast(1f)
                    val expandWidthPx = expand.takeOrElse { 0.dp }.toPx()

                    drawBorderInner(shape, borderCache, alignment, brush, strokeWidthPx, expandWidthPx)
                }
            }
        )

    private fun ContentDrawScope.drawBorderInner(
        shape: Shape,
        borderCache: BorderCache,
        alignment: Stroke.Alignment,
        brush: Brush,
        strokeWidthPx: Float,
        expandWidthPx: Float,
    ) {
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> {
                when (shape) {
                    is RoundedCornerShape ->
                        drawRoundedBorder(
                            borderCache = borderCache,
                            alignment = alignment,
                            outline = Outline.Rounded(RoundRect(outline.rect)),
                            brush = brush,
                            strokeWidthPx = strokeWidthPx,
                            expandWidthPx = expandWidthPx,
                        )

                    else -> drawRectBorder(alignment, outline, brush, strokeWidthPx, expandWidthPx)
                }
            }

            is Outline.Rounded ->
                drawRoundedBorder(borderCache, alignment, outline, brush, strokeWidthPx, expandWidthPx)

            is Outline.Generic ->
                drawGenericBorder(borderCache, alignment, outline, brush, strokeWidthPx, expandWidthPx)
        }
    }

    private fun ContentDrawScope.drawRectBorder(
        alignment: Stroke.Alignment,
        outline: Outline.Rectangle,
        brush: Brush,
        strokeWidthPx: Float,
        expandWidthPx: Float,
    ) {
        val rect =
            when (alignment) {
                Stroke.Alignment.Inside -> outline.rect.inflate(expandWidthPx - strokeWidthPx / 2f)
                Stroke.Alignment.Center -> outline.rect.inflate(expandWidthPx)
                Stroke.Alignment.Outside -> outline.rect.inflate(expandWidthPx + strokeWidthPx / 2f)
            }

        drawRect(brush, rect.topLeft, rect.size, style = DrawScopeStroke(strokeWidthPx))
    }

    private fun ContentDrawScope.drawRoundedBorder(
        borderCache: BorderCache,
        alignment: Stroke.Alignment,
        outline: Outline.Rounded,
        brush: Brush,
        strokeWidthPx: Float,
        expandWidthPx: Float,
    ) {
        val halfStroke = strokeWidthPx / 2f
        val roundRect =
            when (alignment) {
                // Inside: Shift inward by half a stroke so the outer edge matches the boundary
                Stroke.Alignment.Inside -> outline.roundRect.grow(expandWidthPx - halfStroke)
                // Center: Just apply the expansion
                Stroke.Alignment.Center -> outline.roundRect.grow(expandWidthPx)
                // Outside: Shift outward by half a stroke
                Stroke.Alignment.Outside -> outline.roundRect.grow(expandWidthPx + halfStroke)
            }

        if (roundRect.hasAtLeastOneNonRoundedCorner()) {
            val borderPath =
                borderCache.obtainPath().apply {
                    reset()
                    fillType = PathFillType.EvenOdd
                    addRoundRect(roundRect.shrink(halfStroke))
                    addRoundRect(roundRect.grow(halfStroke))
                }
            drawPath(borderPath, brush)
        } else {
            drawOutline(Outline.Rounded(roundRect), brush, style = DrawScopeStroke(strokeWidthPx))
        }
    }

    private fun ContentDrawScope.drawGenericBorder(
        borderCache: BorderCache,
        alignment: Stroke.Alignment,
        outline: Outline.Generic,
        brush: Brush,
        strokeWidth: Float,
        expandWidthPx: Float,
    ) {
        // Get the outer border and inner border inflate delta,
        // the part between inner and outer is the border that
        // needs to be drawn
        val (outer, inner) =
            when (alignment) {
                // Inside border means the outer border inflate delta is 0
                Stroke.Alignment.Inside -> 0f + expandWidthPx to -strokeWidth + expandWidthPx
                Stroke.Alignment.Center -> strokeWidth / 2f + expandWidthPx to -strokeWidth / 2f + expandWidthPx
                Stroke.Alignment.Outside -> strokeWidth + expandWidthPx to 0f + expandWidthPx
            }

        when (outer) {
            inner -> return
            // Simply draw the outline when abs(outer) and abs(inner) are the same
            -inner -> drawOutline(outline, brush, style = DrawScopeStroke(outer * 2f))
            else -> {
                val config: ImageBitmapConfig
                val colorFilter: ColorFilter?
                if (brush is SolidColor) {
                    config = ImageBitmapConfig.Alpha8
                    colorFilter = ColorFilter.tint(brush.value)
                } else {
                    config = ImageBitmapConfig.Argb8888
                    colorFilter = null
                }
                val pathBounds = outline.path.getBounds().inflate(outer)
                val outerMaskPath =
                    borderCache.obtainPath().apply {
                        reset()
                        addRect(pathBounds)
                        op(this, outline.path, PathOperation.Difference)
                    }
                val cacheImageBitmap: ImageBitmap
                val pathBoundsSize = IntSize(ceil(pathBounds.width).toInt(), ceil(pathBounds.height).toInt())

                with(borderCache) {
                    cacheImageBitmap =
                        drawBorderCache(pathBoundsSize, config) {
                            translate(-pathBounds.left, -pathBounds.top) {
                                if (inner < 0f && outer > 0f) {
                                    TODO("Not implemented for generic border")
                                }

                                if (outer > 0f && inner >= 0f) {
                                    drawPath(outline.path, brush, style = DrawScopeStroke(outer * 2f))

                                    if (inner > 0f) {
                                        drawPath(
                                            path = outline.path,
                                            brush = brush,
                                            blendMode = BlendMode.Clear,
                                            style = DrawScopeStroke(inner * 2f),
                                        )
                                    }

                                    drawPath(path = outline.path, brush = brush, blendMode = BlendMode.Clear)
                                }

                                if (outer <= 0f && inner < 0f) {
                                    drawPath(path = outline.path, brush = brush, style = DrawScopeStroke(-inner * 2f))

                                    if (outer < 0f) {
                                        drawPath(
                                            path = outline.path,
                                            brush = brush,
                                            blendMode = BlendMode.Clear,
                                            style = DrawScopeStroke(-outer * 2f),
                                        )
                                    }

                                    drawPath(path = outerMaskPath, brush = brush, blendMode = BlendMode.Clear)
                                }
                            }
                        }
                }

                translate(pathBounds.left, pathBounds.top) {
                    drawImage(cacheImageBitmap, srcSize = pathBoundsSize, colorFilter = colorFilter)
                }
            }
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        shape = this@BorderWithAlignmentNode.shape
    }
}

/**
 * Helper object that handles lazily allocating and re-using objects to render the border into an offscreen ImageBitmap
 */
private class BorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: Canvas? = null,
    private var canvasDrawScope: CanvasDrawScope? = null,
    private var borderPath: Path? = null,
) {
    inline fun ContentDrawScope.drawBorderCache(
        borderSize: IntSize,
        config: ImageBitmapConfig,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {
        var targetImageBitmap = imageBitmap
        var targetCanvas = canvas
        val compatibleConfig =
            targetImageBitmap?.config == ImageBitmapConfig.Argb8888 || config == targetImageBitmap?.config

        @Suppress("ComplexCondition")
        if (
            targetImageBitmap == null ||
                targetCanvas == null ||
                size.width > targetImageBitmap.width ||
                size.height > targetImageBitmap.height ||
                !compatibleConfig
        ) {
            targetImageBitmap =
                ImageBitmap(borderSize.width, borderSize.height, config = config).also { imageBitmap = it }
            targetCanvas = Canvas(targetImageBitmap).also { canvas = it }
        }

        val targetDrawScope = canvasDrawScope ?: CanvasDrawScope().also { canvasDrawScope = it }
        val drawSize = borderSize.toSize()
        targetDrawScope.draw(this, layoutDirection, targetCanvas, drawSize) {
            drawRect(color = Color.Black, size = drawSize, blendMode = BlendMode.Clear)
            block()
        }
        targetImageBitmap.prepareToDraw()
        return targetImageBitmap
    }

    fun obtainPath(): Path = borderPath ?: Path().also { borderPath = it }
}
