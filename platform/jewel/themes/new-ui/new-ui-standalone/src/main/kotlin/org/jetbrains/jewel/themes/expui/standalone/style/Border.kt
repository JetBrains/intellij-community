package org.jetbrains.jewel.themes.expui.standalone.style

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil

fun Modifier.outerBorder(border: BorderStroke, shape: Shape = RectangleShape) =
    outerBorder(width = border.width, brush = border.brush, shape = shape)

fun Modifier.outerBorder(width: Dp, color: Color, shape: Shape = RectangleShape) =
    outerBorder(width, SolidColor(color), shape)

fun Modifier.outerBorder(width: Dp, brush: Brush, shape: Shape): Modifier = composed(
    factory = {
        // BorderCache object that is lazily allocated depending on the type of shape
        // This object is only used for generic shapes and rounded rectangles with different corner
        // radius sizes.
        val borderCacheRef = remember { Ref<BorderCache>() }
        this.then(
            Modifier.drawWithCache {
                val hasValidBorderParams = width.toPx() >= 0f && size.minDimension > 0f
                if (!hasValidBorderParams) {
                    drawContentWithoutBorder()
                } else {
                    val strokeWidthPx = if (width == Dp.Hairline) 1f else ceil(width.toPx())
                    val halfStroke = strokeWidthPx / 2
                    val topLeft = Offset(-halfStroke, -halfStroke)
                    val borderSize = Size(
                        size.width + strokeWidthPx, size.height + strokeWidthPx
                    )
                    when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is Outline.Generic -> TODO("Not support for generic outline")

                        is Outline.Rounded -> drawRoundRectBorder(
                            borderCacheRef, brush, outline, topLeft, borderSize, strokeWidthPx
                        )

                        is Outline.Rectangle -> drawRectBorder(
                            brush, topLeft, borderSize, strokeWidthPx
                        )
                    }
                }
            }
        )
    },
    inspectorInfo = debugInspectorInfo {
        name = "outerBorder"
        properties["width"] = width
        if (brush is SolidColor) {
            properties["color"] = brush.value
            value = brush.value
        } else {
            properties["brush"] = brush
        }
        properties["shape"] = shape
    }
)

private fun Ref<BorderCache>.obtain(): BorderCache = this.value ?: BorderCache().also { value = it }

private class BorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: androidx.compose.ui.graphics.Canvas? = null,
    private var canvasDrawScope: CanvasDrawScope? = null,
    private var borderPath: Path? = null,
) {

    inline fun CacheDrawScope.drawBorderCache(
        borderSize: IntSize,
        config: ImageBitmapConfig,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {

        var targetImageBitmap = imageBitmap
        var targetCanvas = canvas
        // If we previously had allocated a full Argb888 ImageBitmap but are only requiring
        // an alpha mask, just re-use the same ImageBitmap instead of allocating a new one
        val compatibleConfig =
            targetImageBitmap?.config == ImageBitmapConfig.Argb8888 || config == targetImageBitmap?.config
        @Suppress("ComplexCondition")
        if (targetImageBitmap == null || targetCanvas == null
            || size.width > targetImageBitmap.width
            || size.height > targetImageBitmap.height
            || !compatibleConfig) {
            targetImageBitmap = ImageBitmap(
                borderSize.width, borderSize.height, config = config
            ).also {
                imageBitmap = it
            }
            targetCanvas = androidx.compose.ui.graphics.Canvas(targetImageBitmap).also {
                canvas = it
            }
        }

        val targetDrawScope = canvasDrawScope ?: CanvasDrawScope().also { canvasDrawScope = it }
        val drawSize = borderSize.toSize()
        targetDrawScope.draw(
            this, layoutDirection, targetCanvas, drawSize
        ) {
            // Clear the previously rendered portion within this ImageBitmap as we could
            // be re-using it
            drawRect(
                color = Color.Black, size = drawSize, blendMode = BlendMode.Clear
            )
            block()
        }
        targetImageBitmap.prepareToDraw()
        return targetImageBitmap
    }

    fun obtainPath(): Path = borderPath ?: Path().also { borderPath = it }
}

/**
 * Border implementation for invalid parameters that just draws the content
 * as the given border parameters are infeasible (ex. negative border width)
 */
private fun CacheDrawScope.drawContentWithoutBorder(): DrawResult = onDrawWithContent {
    drawContent()
}

/**
 * Border implementation for simple rounded rects and those with different corner
 * radii
 */
private fun CacheDrawScope.drawRoundRectBorder(
    borderCacheRef: Ref<BorderCache>,
    brush: Brush,
    outline: Outline.Rounded,
    topLeft: Offset,
    borderSize: Size,
    strokeWidth: Float,
): DrawResult {
    return if (outline.roundRect.isSimple) {
        val cornerRadius = outline.roundRect.topLeftCornerRadius
        val halfStroke = strokeWidth / 2
        val borderStroke = Stroke(strokeWidth)
        onDrawWithContent {
            drawContent()
            // Otherwise draw a stroked rounded rect with the corner radius
            // shrunk by half of the stroke width. This will ensure that the
            // outer curvature of the rounded rectangle will have the desired
            // corner radius.
            drawRoundRect(
                brush = brush,
                topLeft = topLeft,
                size = borderSize,
                cornerRadius = cornerRadius.expand(halfStroke),
                style = borderStroke
            )
        }
    } else {
        val path = borderCacheRef.obtain().obtainPath()
        val roundedRectPath = createRoundRectPath(path, outline.roundRect, strokeWidth)
        onDrawWithContent {
            drawContent()
            drawPath(roundedRectPath, brush = brush)
        }
    }
}

/**
 * Border implementation for rectangular borders
 */
private fun CacheDrawScope.drawRectBorder(
    brush: Brush,
    topLeft: Offset,
    borderSize: Size,
    strokeWidthPx: Float,
): DrawResult {
    // If we are drawing a rectangular stroke, just offset it by half the stroke
    // width as strokes are always drawn centered on their geometry.
    // If the border is larger than the drawing area, just fill the area with a
    // solid rectangle
    val style = Stroke(strokeWidthPx)
    return onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = brush,
            topLeft = topLeft,
            size = borderSize,
            cornerRadius = CornerRadius(strokeWidthPx),
            style = style
        )
    }
}

private fun createRoundRectPath(
    targetPath: Path,
    roundedRect: RoundRect,
    strokeWidth: Float,
): Path = targetPath.apply {
    reset()
    addRoundRect(roundedRect)
    val insetPath = Path().apply {
        addRoundRect(createInsetRoundedRect(strokeWidth, roundedRect))
    }
    op(this, insetPath, PathOperation.Difference)
}

private fun createInsetRoundedRect(
    widthPx: Float,
    roundedRect: RoundRect,
) = RoundRect(
    left = -widthPx,
    top = -widthPx,
    right = roundedRect.width + widthPx,
    bottom = roundedRect.height + widthPx,
    topLeftCornerRadius = roundedRect.topLeftCornerRadius.expand(widthPx),
    topRightCornerRadius = roundedRect.topRightCornerRadius.expand(widthPx),
    bottomLeftCornerRadius = roundedRect.bottomLeftCornerRadius.expand(widthPx),
    bottomRightCornerRadius = roundedRect.bottomRightCornerRadius.expand(widthPx)
)

private fun CornerRadius.expand(value: Float): CornerRadius = CornerRadius(this.x + value, this.y + value)
