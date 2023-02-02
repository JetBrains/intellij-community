package org.jetbrains.jewel.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.InspectorValueInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.components.ImageSlice
import org.jetbrains.jewel.components.ImageSliceValues
import org.jetbrains.jewel.shape.QuadRoundedCornerShape
import org.jetbrains.jewel.shape.addQuadRoundRect

fun Modifier.background(image: ImageBitmap, maintainAspect: Boolean = true): Modifier =
    then(
        DrawImageBackgroundModifier(
            image,
            maintainAspect,
            debugInspectorInfo {
                name = "background"
                properties["image"] = image
            }
        )
    )

fun Modifier.background(image: ImageBitmap, slices: ImageSliceValues): Modifier =
    background(ImageSlice(image, slices))

fun Modifier.background(imageSlice: ImageSlice): Modifier =
    then(
        DrawImageSliceBackgroundModifier(
            imageSlice,
            debugInspectorInfo {
                name = "background"
                properties["image"] = imageSlice.image
                properties["slices"] = imageSlice.slices
            }
        )
    )

fun Modifier.background(
    color: Color,
    shape: Shape = RectangleShape
) = this.then(
    Background(
        color = color,
        shape = shape,
        inspectorInfo = debugInspectorInfo {
            name = "background"
            value = color
            properties["color"] = color
            properties["shape"] = shape
        }
    )
)

fun Modifier.background(
    brush: Brush,
    shape: Shape = RectangleShape
) = this.then(
    Background(
        brush = brush,
        shape = shape,
        inspectorInfo = debugInspectorInfo {
            name = "background"
            value = brush
            properties["brush"] = brush
            properties["shape"] = shape
        }
    )
)

private class Background constructor(
    private val color: Color? = null,
    private val brush: Brush? = null,
    private val alpha: Float = 1.0f,
    private val shape: Shape,
    inspectorInfo: InspectorInfo.() -> Unit
) : DrawModifier, InspectorValueInfo(inspectorInfo) {

    private var lastSize: Size? = null
    private var lastLayoutDirection: LayoutDirection? = null
    private var lastOutline: Outline? = null

    override fun ContentDrawScope.draw() {
        if (shape === RectangleShape) {
            drawRect()
        } else {
            drawOutline()
        }
        drawContent()
    }

    private fun ContentDrawScope.drawRect() {
        color?.let { drawRect(color = it) }
        brush?.let { drawRect(brush = it, alpha = alpha) }
    }

    private fun ContentDrawScope.drawOutline() {
        val outline =
            if (size == lastSize && layoutDirection == lastLayoutDirection) {
                lastOutline!!
            } else {
                shape.createOutline(size, layoutDirection, this)
            }

        if (outline is Outline.Rounded && shape is QuadRoundedCornerShape) {
            val path = Path().apply {
                addQuadRoundRect(outline.roundRect)
            }
            color?.let { drawPath(path, color = color) }
            brush?.let { drawPath(path, brush = brush, alpha = alpha) }
        } else {
            color?.let { drawOutline(outline, color = color) }
            brush?.let { drawOutline(outline, brush = brush, alpha = alpha) }
        }
        lastOutline = outline
        lastSize = size
    }

    override fun hashCode(): Int {
        var result = color?.hashCode() ?: 0
        result = 31 * result + (brush?.hashCode() ?: 0)
        result = 31 * result + alpha.hashCode()
        result = 31 * result + shape.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        val otherModifier = other as? Background ?: return false
        return color == otherModifier.color &&
            brush == otherModifier.brush &&
            alpha == otherModifier.alpha &&
            shape == otherModifier.shape
    }

    override fun toString(): String =
        "Background(color=$color, brush=$brush, alpha=$alpha, shape=$shape)"
}

abstract class CustomBackgroundModifier(
    inspectorInfo: InspectorInfo.() -> Unit
) : DrawModifier,
    InspectorValueInfo(inspectorInfo) {

    override fun ContentDrawScope.draw() {
        drawBackground()
        drawContent()
    }

    abstract fun DrawScope.drawBackground()
}

private class DrawImageBackgroundModifier(
    val image: ImageBitmap,
    val maintainAspect: Boolean,
    inspectorInfo: InspectorInfo.() -> Unit
) : CustomBackgroundModifier(inspectorInfo) {

    override fun DrawScope.drawBackground() {
        val width = size.width.toInt()
        val height = size.height.toInt()
        if (maintainAspect) {
            val imageWidth = image.width
            val imageHeight = image.height
            val imageAspect = imageWidth.toDouble() / imageHeight
            val areaAspect = width.toDouble() / height
            val srcWidth = if (imageAspect > areaAspect) (imageHeight * areaAspect).toInt() else imageWidth
            val srcHeight = if (imageAspect < areaAspect) (imageWidth / areaAspect).toInt() else imageHeight

            drawImage(image, srcSize = IntSize(srcWidth, srcHeight), dstSize = IntSize(width, height))
        } else {
            drawImage(image, dstSize = IntSize(width, height))
        }
    }
}

private class DrawImageSliceBackgroundModifier(
    val imageSlice: ImageSlice,
    inspectorInfo: InspectorInfo.() -> Unit
) : CustomBackgroundModifier(inspectorInfo) {

    override fun DrawScope.drawBackground() = imageSlice.draw(this)
}
