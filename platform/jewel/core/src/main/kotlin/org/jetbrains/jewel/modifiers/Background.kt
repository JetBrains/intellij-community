package org.jetbrains.jewel.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.InspectorValueInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.IntSize
import org.jetbrains.jewel.components.ImageSlice
import org.jetbrains.jewel.components.ImageSliceValues

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
