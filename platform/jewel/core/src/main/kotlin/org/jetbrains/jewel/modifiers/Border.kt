package org.jetbrains.jewel.modifiers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import org.jetbrains.jewel.shape.QuadRoundedCornerShape
import org.jetbrains.jewel.shape.addQuadRoundRect
import kotlin.math.ceil
import kotlin.math.min

fun Modifier.border(alignment: BorderAlignment, border: BorderStroke, shape: Shape = RectangleShape) =
    border(alignment = alignment, width = border.width, brush = border.brush, shape = shape)

fun Modifier.border(alignment: BorderAlignment, width: Dp, color: Color, shape: Shape = RectangleShape) =
    border(alignment, width, SolidColor(color), shape)

fun Modifier.border(alignment: BorderAlignment, width: Dp, brush: Brush, shape: Shape): Modifier =
    if (alignment == BorderAlignment.Inside && shape !is QuadRoundedCornerShape) {
        // The compose native border modifier(androidx.compose.foundation.border) draws the border inside the shape,
        // so we can just use that for getting a more native experience when drawing inside borders
        border(width, brush, shape)
    } else {
        drawBorderWithAlignment(alignment, width, brush, shape)
    }

enum class BorderAlignment {
    Inside, Center, Outside
}

private fun Modifier.drawBorderWithAlignment(alignment: BorderAlignment, width: Dp, brush: Brush, shape: Shape): Modifier = composed(
    factory = {
        val borderCacheRef = remember { Ref<BorderCache>() }
        this.then(
            Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val strokeWidthPx = min(
                        if (width == Dp.Hairline) 1f else ceil(width.toPx()),
                        ceil(size.minDimension / 2)
                    )
                    when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is Outline.Rectangle -> {
                            when (shape) {
                                is RoundedCornerShape -> drawRoundedBorder(
                                    borderCacheRef,
                                    alignment,
                                    Outline.Rounded(RoundRect(outline.rect)),
                                    brush,
                                    strokeWidthPx
                                )

                                is QuadRoundedCornerShape -> drawQuadRoundedBorder(
                                    borderCacheRef,
                                    alignment,
                                    Outline.Rounded(RoundRect(outline.rect)),
                                    brush,
                                    strokeWidthPx
                                )

                                else -> drawRectBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx)
                            }
                        }

                        is Outline.Rounded -> {
                            when (shape) {
                                is QuadRoundedCornerShape -> drawQuadRoundedBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx)
                                else -> drawRoundedBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx)
                            }
                        }

                        is Outline.Generic -> drawGenericBorder(borderCacheRef, alignment, outline, brush, strokeWidthPx)
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
    }
)

private class BorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: androidx.compose.ui.graphics.Canvas? = null,
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
            targetCanvas = androidx.compose.ui.graphics.Canvas(targetImageBitmap).also {
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

private fun ContentDrawScope.drawRectBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: BorderAlignment,
    outline: Outline.Rectangle,
    brush: Brush,
    strokeWidthPx: Float
) {
    when (alignment) {
        BorderAlignment.Inside -> {
            val rect = outline.rect.deflate(strokeWidthPx / 2f)
            drawRect(brush, rect.topLeft, rect.size, style = Stroke(strokeWidthPx))
        }

        BorderAlignment.Center -> {
            drawOutline(outline, brush, style = Stroke(strokeWidthPx))
        }

        BorderAlignment.Outside -> {
            val rect = outline.rect.inflate(strokeWidthPx / 2f)
            drawRect(brush, rect.topLeft, rect.size, style = Stroke(strokeWidthPx))
        }
    }
}

private fun ContentDrawScope.drawRoundedBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: BorderAlignment,
    outline: Outline.Rounded,
    brush: Brush,
    strokeWidthPx: Float
) {
    when (alignment) {
        BorderAlignment.Inside -> {
            val rrect = outline.roundRect.deflate(strokeWidthPx / 2f)
            val radius = rrect.bottomLeftCornerRadius.x
            drawRoundRect(
                brush = brush,
                topLeft = Offset(rrect.top, rrect.left),
                size = Size(rrect.width, rrect.height),
                cornerRadius = CornerRadius(radius),
                style = Stroke(strokeWidthPx)
            )
        }

        BorderAlignment.Center -> {
            val rrect = outline.roundRect
            val radius = rrect.bottomLeftCornerRadius.x

            if (radius == 0f) {
                val cache = borderCacheRef.obtain()
                val borderPath = cache.obtainPath().apply {
                    reset()
                    fillType = PathFillType.EvenOdd
                    addRoundRect(outline.roundRect.deflate(strokeWidthPx / 2f))
                    addRoundRect(outline.roundRect.inflate(strokeWidthPx / 2f))
                }
                drawPath(borderPath, brush)
            } else {
                drawOutline(outline, brush, style = Stroke(strokeWidthPx))
            }
        }

        BorderAlignment.Outside -> {
            val rrect = outline.roundRect.inflate(strokeWidthPx / 2f)
            val radius = rrect.bottomLeftCornerRadius.x
            drawRoundRect(
                brush = brush,
                topLeft = Offset(rrect.top, rrect.left),
                size = Size(rrect.width, rrect.height),
                cornerRadius = CornerRadius(radius),
                style = Stroke(strokeWidthPx)
            )
        }
    }
}

private fun ContentDrawScope.drawQuadRoundedBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: BorderAlignment,
    outline: Outline.Rounded,
    brush: Brush,
    strokeWidthPx: Float
) {
    when (alignment) {
        BorderAlignment.Inside -> {
            val cache = borderCacheRef.obtain()
            val borderPath = cache.obtainPath().apply {
                reset()
                addQuadRoundRect(outline.roundRect.deflate(strokeWidthPx / 2f))
            }
            drawPath(borderPath, brush, style = Stroke(strokeWidthPx))
        }

        BorderAlignment.Center -> {
            val rrect = outline.roundRect
            val radius = rrect.bottomLeftCornerRadius.x
            val cache = borderCacheRef.obtain()

            if (radius == 0f) {
                val borderPath = cache.obtainPath().apply {
                    reset()
                    fillType = PathFillType.EvenOdd
                    addQuadRoundRect(outline.roundRect.deflate(strokeWidthPx / 2f))
                    addQuadRoundRect(outline.roundRect.inflate(strokeWidthPx / 2f))
                }
                drawPath(borderPath, brush)
            } else {
                val borderPath = cache.obtainPath().apply {
                    reset()
                    addQuadRoundRect(outline.roundRect)
                }
                drawPath(borderPath, brush, style = Stroke(strokeWidthPx))
            }
        }

        BorderAlignment.Outside -> {
            val cache = borderCacheRef.obtain()
            val borderPath = cache.obtainPath().apply {
                reset()
                addQuadRoundRect(outline.roundRect.inflate(strokeWidthPx / 2f))
            }
            drawPath(borderPath, brush, style = Stroke(strokeWidthPx))
        }
    }
}

private fun CacheDrawScope.drawGenericBorder(
    borderCacheRef: Ref<BorderCache>,
    alignment: BorderAlignment,
    outline: Outline.Generic,
    brush: Brush,
    strokeWidth: Float
): DrawResult = onDrawWithContent {
    drawContent()
    when (alignment) {
        BorderAlignment.Inside -> {
            val config: ImageBitmapConfig
            val colorFilter: ColorFilter?
            if (brush is SolidColor) {
                config = ImageBitmapConfig.Alpha8
                colorFilter = ColorFilter.tint(brush.value)
            } else {
                config = ImageBitmapConfig.Argb8888
                colorFilter = null
            }
            val pathBounds = outline.path.getBounds()
            val borderCache = borderCacheRef.obtain()
            val maskPath = borderCache.obtainPath().apply {
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
                        drawPath(path = outline.path, brush = brush, style = Stroke(strokeWidth * 2))

                        drawPath(path = maskPath, brush = brush, blendMode = BlendMode.Clear)
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

        BorderAlignment.Center -> {
            drawOutline(outline, brush, style = Stroke(strokeWidth))
        }

        BorderAlignment.Outside -> {
            val config: ImageBitmapConfig
            val colorFilter: ColorFilter?
            if (brush is SolidColor) {
                config = ImageBitmapConfig.Alpha8
                colorFilter = ColorFilter.tint(brush.value)
            } else {
                config = ImageBitmapConfig.Argb8888
                colorFilter = null
            }
            val pathBounds = outline.path.getBounds().inflate(strokeWidth)
            val borderCache = borderCacheRef.obtain()
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
                        drawPath(path = outline.path, brush = brush, style = Stroke(strokeWidth * 2))

                        drawPath(path = outline.path, brush = brush, blendMode = BlendMode.Clear)
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

fun RoundRect.inflate(delta: Float) = RoundRect(
    left = left - delta,
    top = top - delta,
    right = right + delta,
    bottom = bottom + delta,
    topLeftCornerRadius = CornerRadius(topLeftCornerRadius.x + delta, topLeftCornerRadius.y + delta),
    topRightCornerRadius = CornerRadius(topRightCornerRadius.x + delta, topRightCornerRadius.y + delta),
    bottomLeftCornerRadius = CornerRadius(bottomLeftCornerRadius.x + delta, bottomLeftCornerRadius.y + delta),
    bottomRightCornerRadius = CornerRadius(bottomRightCornerRadius.x + delta, bottomRightCornerRadius.y + delta)
)

fun RoundRect.deflate(delta: Float) = RoundRect(
    left = left + delta,
    top = top + delta,
    right = right - delta,
    bottom = bottom - delta,
    topLeftCornerRadius = CornerRadius(topLeftCornerRadius.x - delta, topLeftCornerRadius.y - delta),
    topRightCornerRadius = CornerRadius(topRightCornerRadius.x - delta, topRightCornerRadius.y - delta),
    bottomLeftCornerRadius = CornerRadius(bottomLeftCornerRadius.x - delta, bottomLeftCornerRadius.y - delta),
    bottomRightCornerRadius = CornerRadius(bottomRightCornerRadius.x - delta, bottomRightCornerRadius.y - delta)
)
