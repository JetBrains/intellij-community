package org.jetbrains.jewel

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp

fun Modifier.shape(shape: Shape, shapeStroke: ShapeStroke<*>? = null, fillColor: Color = Color.Unspecified): Modifier =
    shape(shape, shapeStroke, fillColor.nullIfUnspecified()?.toBrush())

fun Modifier.shape(shape: Shape, shapeStroke: ShapeStroke<*>? = null, fillBrush: Brush?): Modifier =
    composed(
        factory = {
            this.then(
                when {
                    shape === RectangleShape -> rectangleModifier(shapeStroke, fillBrush)
                    else -> shapeModifier(shapeStroke, fillBrush, shape)
                }
            )
        },
        inspectorInfo = debugInspectorInfo {
            name = "shape"
            properties["stroke"] = shapeStroke
            properties["shape"] = shape
        }
    )

private fun rectangleModifier(shapeStroke: ShapeStroke<*>?, brush: Brush?) = Modifier.drawWithCache {
    if (shapeStroke != null) {
        val strokeWidth = if (shapeStroke.width == Dp.Hairline) 1f else shapeStroke.width.toPx()
        val stroke = Stroke(strokeWidth)
        val insets = shapeStroke.insets
        val insetOffset = Offset(insets.left.toPx(), insets.top.toPx())
        val insetSize = Size(
            size.width - insets.left.toPx() - insets.right.toPx(),
            size.height - insets.top.toPx() - insets.bottom.toPx()
        )
        drawRectangleShape(insetOffset, insetSize, stroke, shapeStroke.brush, brush)
    } else {
        drawRectangleShape(Offset.Zero, size, null, null, brush)
    }
}

private fun CacheDrawScope.drawRectangleShape(
    insetOffset: Offset,
    insetSize: Size,
    stroke: Stroke?,
    strokeBrush: Brush?,
    fillBrush: Brush?
) =
    onDrawWithContent {
        val strokeWidth = stroke?.width ?: 0f
        val enoughSpace = size.width > strokeWidth && size.height > strokeWidth
        if (fillBrush != null && enoughSpace) {
            drawRect(brush = fillBrush, topLeft = insetOffset, size = insetSize, style = Fill)
        }
        drawContent()
        if (stroke != null && strokeBrush != null && enoughSpace)
            drawRect(brush = strokeBrush, topLeft = insetOffset, size = insetSize, style = stroke)
    }

private fun CacheDrawScope.drawRoundedShape(
    insetOffset: Offset,
    outline: Outline.Rounded,
    stroke: Stroke?,
    strokeBrush: Brush?,
    fillBrush: Brush?
) =
    onDrawWithContent {
        when {
            outline.roundRect.isSimple -> {
                val roundRect = outline.roundRect
                if (fillBrush != null) {
                    withTransform({ translate(insetOffset.x, insetOffset.y) }) {
                        drawRoundRect(
                            brush = fillBrush,
                            topLeft = Offset(roundRect.left, roundRect.top),
                            size = Size(roundRect.width, roundRect.height),
                            cornerRadius = roundRect.topLeftCornerRadius,
                            style = Fill
                        )
                    }
                }
                drawContent()
                if (stroke != null && strokeBrush != null)
                    withTransform({ translate(insetOffset.x, insetOffset.y) }) {
                        drawRoundRect(
                            brush = strokeBrush,
                            topLeft = Offset(roundRect.left, roundRect.top),
                            size = Size(roundRect.width, roundRect.height),
                            cornerRadius = roundRect.topLeftCornerRadius,
                            style = stroke
                        )
                    }
            }

            else -> {
                val path = Path().apply {
                    addRoundRect(outline.roundRect)
                    translate(insetOffset)
                }
                if (fillBrush != null) {
                    drawPath(path, brush = fillBrush, style = Fill)
                }
                drawContent()
                if (stroke != null && strokeBrush != null)
                    drawPath(path, strokeBrush, style = stroke)
            }
        }
    }

private fun CacheDrawScope.drawPathShape(path: Path, stroke: Stroke?, strokeBrush: Brush?, fillBrush: Brush?) =
    onDrawWithContent {
        if (fillBrush != null) {
            drawPath(path, brush = fillBrush, style = Fill)
        }
        drawContent()
        if (stroke != null && strokeBrush != null)
            drawPath(path, strokeBrush, style = stroke)
    }

private fun shapeModifier(shapeStroke: ShapeStroke<*>?, fillBrush: Brush?, shape: Shape) = Modifier.drawWithCache {
    val strokeWidth = when (shapeStroke?.width) {
        null -> 0f
        Dp.Hairline -> 1f
        else -> shapeStroke.width.toPx()
    }
    val insets = shapeStroke?.insets ?: Insets.Empty
    val insetOffset = Offset(insets.left.toPx(), insets.top.toPx())
    val insetSize = Size(
        size.width - insets.left.toPx() - insets.right.toPx(),
        size.height - insets.top.toPx() - insets.bottom.toPx()
    )
    val stroke = if (shapeStroke != null) Stroke(strokeWidth) else null
    val strokeBrush = shapeStroke?.brush
    val outline: Outline = shape.createOutline(insetSize, layoutDirection, this)

    when {
        size.minDimension > 0f -> when (outline) {
            is Outline.Rectangle -> drawRectangleShape(insetOffset, insetSize, stroke, strokeBrush, fillBrush)
            is Outline.Rounded -> drawRoundedShape(insetOffset, outline, stroke, strokeBrush, fillBrush)
            is Outline.Generic -> {
                val path = Path().apply { addPath(outline.path, insetOffset) }
                drawPathShape(path, stroke, strokeBrush, fillBrush)
            }
        }

        else -> onDrawWithContent {
            drawContent()
        }
    }
}

fun Color.toBrush() = SolidColor(this)

private fun Color.nullIfUnspecified() = takeIf { it != Color.Unspecified }
