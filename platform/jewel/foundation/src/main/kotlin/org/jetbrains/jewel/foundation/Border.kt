package org.jetbrains.jewel.foundation

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

typealias DrawScopeStroke = androidx.compose.ui.graphics.drawscope.Stroke

fun Modifier.border(stroke: Stroke, shape: Shape): Modifier = when (stroke) {
    is Stroke.None -> this
    is Stroke.Solid -> border(stroke.alignment, stroke.width, stroke.color, shape, stroke.expand)
    is Stroke.Brush -> border(
        alignment = stroke.alignment,
        width = stroke.width,
        brush = stroke.brush,
        shape = shape,
        expand = stroke.expand
    )
}

fun Modifier.border(alignment: Stroke.Alignment, width: Dp, color: Color, shape: Shape = RectangleShape, expand: Dp = Dp.Unspecified) =
    border(alignment, width, SolidColor(color), shape, expand)

fun Modifier.border(alignment: Stroke.Alignment, width: Dp, brush: Brush, shape: Shape = RectangleShape, expand: Dp = Dp.Unspecified): Modifier =
    if (alignment == Stroke.Alignment.Inside && expand.isUnspecified) {
        // The compose native border modifier(androidx.compose.foundation.border) draws the border inside the shape,
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
    expand: Dp
): Modifier = composed(
    factory = {
        val borderCacheRef = remember { Ref<BorderCache>() }
        this.then(
            Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val strokeWidthPx = max(
                        min(
                            if (width == Dp.Hairline) 1f else ceil(width.toPx()),
                            ceil(size.minDimension / 2)
                        ),
                        1f
                    )
                    val expandWidthPx = expand.takeOrElse { Dp.Hairline }.toPx()
                    when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is Outline.Rectangle -> {
                            when (shape) {
                                is RoundedCornerShape -> drawRoundedBorder(
                                    borderCacheRef,
                                    alignment,
                                    Outline.Rounded(RoundRect(outline.rect)),
                                    brush,
                                    strokeWidthPx,
                                    expandWidthPx
                                )

                                else -> drawRectBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx, expandWidthPx)
                            }
                        }

                        is Outline.Rounded -> drawRoundedBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx, expandWidthPx)

                        is Outline.Generic -> drawGenericBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx, expandWidthPx)
                    }
                }
            }
        )
    },
    inspectorInfo = debugInspectorInfo {
        name = "border"
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
)

private class BorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: Canvas? = null,
    private var canvasDrawScope: CanvasDrawScope? = null,
    private var borderPath: Path? = null
) {

    inline fun ContentDrawScope.drawBorderCache(
        borderSize: IntSize,
        config: ImageBitmapConfig,
        block: DrawScope.() -> Unit
    ): ImageBitmap {
        var targetImageBitmap = imageBitmap
        var targetCanvas = canvas
        val compatibleConfig = targetImageBitmap?.config == ImageBitmapConfig.Argb8888 ||
            config == targetImageBitmap?.config
        @Suppress("ComplexCondition")
        if (targetImageBitmap == null ||
            targetCanvas == null ||
            size.width > targetImageBitmap.width ||
            size.height > targetImageBitmap.height ||
            !compatibleConfig
        ) {
            targetImageBitmap = ImageBitmap(
                borderSize.width,
                borderSize.height,
                config = config
            ).also {
                imageBitmap = it
            }
            targetCanvas = Canvas(targetImageBitmap).also {
                canvas = it
            }
        }

        val targetDrawScope = canvasDrawScope ?: CanvasDrawScope().also { canvasDrawScope = it }
        val drawSize = borderSize.toSize()
        targetDrawScope.draw(
            this,
            layoutDirection,
            targetCanvas,
            drawSize
        ) {
            drawRect(
                color = Color.Black,
                size = drawSize,
                blendMode = BlendMode.Clear
            )
            block()
        }
        targetImageBitmap.prepareToDraw()
        return targetImageBitmap
    }

    fun obtainPath(): Path =
        borderPath ?: Path().also { borderPath = it }
}

private fun Ref<BorderCache>.obtain(): BorderCache =
    this.value ?: BorderCache().also { value = it }

@Suppress("UNUSED_PARAMETER")
private fun ContentDrawScope.drawRectBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: Stroke.Alignment,
    outline: Outline.Rectangle,
    brush: Brush,
    strokeWidthPx: Float,
    expandWidthPx: Float
) {
    val rect = when (alignment) {
        Stroke.Alignment.Inside -> {
            outline.rect.inflate(expandWidthPx - strokeWidthPx / 2f)
        }

        Stroke.Alignment.Center -> {
            outline.rect.inflate(expandWidthPx)
        }

        Stroke.Alignment.Outside -> {
            outline.rect.inflate(expandWidthPx + strokeWidthPx / 2f)
        }
    }
    drawRect(brush, rect.topLeft, rect.size, style = DrawScopeStroke(strokeWidthPx))
}

private fun ContentDrawScope.drawRoundedBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: Stroke.Alignment,
    outline: Outline.Rounded,
    brush: Brush,
    strokeWidthPx: Float,
    expandWidthPx: Float
) {
    val rrect = when (alignment) {
        Stroke.Alignment.Inside -> outline.roundRect.inflate(expandWidthPx - strokeWidthPx / 2f)
        Stroke.Alignment.Center -> outline.roundRect.inflate(expandWidthPx)
        Stroke.Alignment.Outside -> outline.roundRect.inflate(expandWidthPx + strokeWidthPx / 2f)
    }

    if (rrect.hasRightAngle()) {
        val cache = borderCacheRef.obtain()
        val borderPath = cache.obtainPath().apply {
            reset()
            fillType = PathFillType.EvenOdd
            addRoundRect(rrect.deflate(strokeWidthPx / 2f))
            addRoundRect(rrect.inflate(strokeWidthPx / 2f))
        }
        drawPath(borderPath, brush)
    } else {
        drawOutline(
            outline = Outline.Rounded(rrect),
            brush = brush,
            style = DrawScopeStroke(strokeWidthPx)
        )
    }
}

private fun CacheDrawScope.drawGenericBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: Stroke.Alignment,
    outline: Outline.Generic,
    brush: Brush,
    strokeWidth: Float,
    expandWidthPx: Float
): DrawResult = onDrawWithContent {
    drawContent()

    // Get the outer border and inner border inflate delta,
    // the part between inner and outer is the border that
    // needs to be drawn
    val (outer, inner) = when (alignment) {
        Stroke.Alignment.Inside -> {
            // Inside border means the outer border inflate delta is 0
            0f + expandWidthPx to -strokeWidth + expandWidthPx
        }

        Stroke.Alignment.Center -> {
            strokeWidth / 2f + expandWidthPx to -strokeWidth / 2f + expandWidthPx
        }

        Stroke.Alignment.Outside -> {
            strokeWidth + expandWidthPx to 0f + expandWidthPx
        }
    }

    // Nothing need to draw
    when (outer) {
        inner -> {
            return@onDrawWithContent
        }
        -inner -> {
            // Samply draw the outline when abs(outer) and abs(inner) are the same
            drawOutline(outline, brush, style = DrawScopeStroke(outer * 2f))
        }
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
            val borderCache = borderCacheRef.obtain()
            val outerMaskPath = borderCache.obtainPath().apply {
                reset()
                addRect(pathBounds)
                op(this, outline.path, PathOperation.Difference)
            }
            val cacheImageBitmap: ImageBitmap
            val pathBoundsSize = IntSize(
                ceil(pathBounds.width).toInt(),
                ceil(pathBounds.height).toInt()
            )

            with(borderCache) {
                cacheImageBitmap = drawBorderCache(
                    pathBoundsSize,
                    config
                ) {
                    translate(-pathBounds.left, -pathBounds.top) {
                        if (inner < 0f && outer > 0f) {
                            TODO("Not implemented for generic border")
                        }

                        if (outer > 0f && inner >= 0f) {
                            drawPath(path = outline.path, brush = brush, style = DrawScopeStroke(outer * 2f))

                            if (inner > 0f) {
                                drawPath(path = outline.path, brush = brush, blendMode = BlendMode.Clear, style = DrawScopeStroke(inner * 2f))
                            }

                            drawPath(path = outline.path, brush = brush, blendMode = BlendMode.Clear)
                        }

                        if (outer <= 0f && inner < 0f) {
                            drawPath(path = outline.path, brush = brush, style = DrawScopeStroke(-inner * 2f))

                            if (outer < 0f) {
                                drawPath(path = outline.path, brush = brush, blendMode = BlendMode.Clear, style = DrawScopeStroke(-outer * 2f))
                            }

                            drawPath(path = outerMaskPath, brush = brush, blendMode = BlendMode.Clear)
                        }
                    }
                }
            }

            onDrawWithContent {
                drawContent()
                translate(pathBounds.left, pathBounds.top) {
                    drawImage(cacheImageBitmap, srcSize = pathBoundsSize, colorFilter = colorFilter)
                }
            }
        }
    }
}
