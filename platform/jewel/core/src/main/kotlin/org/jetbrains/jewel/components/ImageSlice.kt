package org.jetbrains.jewel.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

fun imageSlices(all: Int) = ImageSliceValues(all)
fun imageSlices(horizontal: Int, vertical: Int) = ImageSliceValues(horizontal, vertical)
fun imageSlices(left: Int, top: Int, right: Int, bottom: Int) = ImageSliceValues(left, top, right, bottom)

// todo: think about RTL?
@Immutable
data class ImageSliceValues(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {

    constructor(all: Int) : this(all, all, all, all)
    constructor(horizontal: Int, vertical: Int) : this(horizontal, vertical, horizontal, vertical)

    val horizontal get() = left + right
    val vertical get() = top + bottom
}

@Immutable
data class ImageSlice(val image: ImageBitmap, val slices: ImageSliceValues) {

    fun draw(
        scope: DrawScope,
        alpha: Float = DefaultAlpha,
        colorFilter: ColorFilter? = null
    ) {
        val area = IntSize(scope.size.width.roundToInt(), scope.size.height.roundToInt())
        val hMiddleSize = image.width - slices.horizontal
        val vMiddleSize = image.height - slices.vertical
        val hTimes = (area.width - slices.horizontal) / hMiddleSize
        val vTimes = (area.height - slices.vertical) / vMiddleSize
        val hExtra = area.width - slices.horizontal - hTimes * hMiddleSize
        val vExtra = area.height - slices.vertical - vTimes * vMiddleSize

        // top row
        scope.drawSlice(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(slices.left, slices.top),
            dstOffset = IntOffset.Zero,
            alpha = alpha,
            colorFilter = colorFilter
        )
        repeat(hTimes) { h ->
            scope.drawSlice(
                image,
                srcOffset = IntOffset(slices.left, 0),
                srcSize = IntSize(hMiddleSize, slices.top),
                dstOffset = IntOffset(slices.left + h * hMiddleSize, 0),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        if (hExtra > 0) {
            scope.drawSlice(
                image,
                srcOffset = IntOffset(slices.left, 0),
                srcSize = IntSize(hExtra, slices.top),
                dstOffset = IntOffset(area.width - slices.right - hExtra, 0),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        scope.drawSlice(
            image,
            srcOffset = IntOffset(image.width - slices.right, 0),
            srcSize = IntSize(slices.right, slices.top),
            dstOffset = IntOffset(area.width - slices.right, 0),
            alpha = alpha,
            colorFilter = colorFilter
        )

        // left and right
        repeat(vTimes) { v ->
            scope.drawSlice(
                image,
                srcOffset = IntOffset(0, slices.top),
                srcSize = IntSize(slices.left, vMiddleSize),
                dstOffset = IntOffset(0, slices.top + v * vMiddleSize),
                alpha = alpha,
                colorFilter = colorFilter
            )
            scope.drawSlice(
                image,
                srcOffset = IntOffset(image.width - slices.right, slices.top),
                srcSize = IntSize(slices.right, vMiddleSize),
                dstOffset = IntOffset(area.width - slices.right, slices.top + v * vMiddleSize),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }
        if (vExtra > 0) {
            scope.drawSlice(
                image,
                srcOffset = IntOffset(0, slices.top),
                srcSize = IntSize(slices.left, vExtra),
                dstOffset = IntOffset(0, area.height - slices.bottom - vExtra),
                alpha = alpha,
                colorFilter = colorFilter
            )
            scope.drawSlice(
                image,
                srcOffset = IntOffset(image.width - slices.right, slices.top),
                srcSize = IntSize(slices.right, vExtra),
                dstOffset = IntOffset(area.width - slices.right, area.height - slices.bottom - vExtra),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        // filler
        repeat(vTimes) { v ->
            repeat(hTimes) { h ->
                scope.drawSlice(
                    image,
                    srcOffset = IntOffset(slices.left, slices.top),
                    srcSize = IntSize(hMiddleSize, vMiddleSize),
                    dstOffset = IntOffset(slices.left + h * hMiddleSize, slices.top + v * vMiddleSize),
                    alpha = alpha,
                    colorFilter = colorFilter
                )
            }
            if (hExtra > 0) {
                scope.drawSlice(
                    image,
                    srcOffset = IntOffset(slices.left, slices.top),
                    srcSize = IntSize(hExtra, vMiddleSize),
                    dstOffset = IntOffset(area.width - slices.right - hExtra, slices.top + v * vMiddleSize),
                    alpha = alpha,
                    colorFilter = colorFilter
                )
            }
        }

        if (vExtra > 0) {
            repeat(hTimes) { h ->
                scope.drawSlice(
                    image,
                    srcOffset = IntOffset(slices.left, slices.top),
                    srcSize = IntSize(hMiddleSize, vExtra),
                    dstOffset = IntOffset(slices.left + h * hMiddleSize, area.height - slices.bottom - vExtra),
                    alpha = alpha,
                    colorFilter = colorFilter
                )
            }
            if (hExtra > 0) {
                scope.drawSlice(
                    image,
                    srcOffset = IntOffset(slices.left, slices.top),
                    srcSize = IntSize(hExtra, vExtra),
                    dstOffset = IntOffset(
                        area.width - slices.right - hExtra,
                        area.height - slices.bottom - vExtra
                    ),
                    alpha = alpha,
                    colorFilter = colorFilter
                )
            }
        }

        // bottom row
        scope.drawSlice(
            image,
            srcOffset = IntOffset(0, image.height - slices.bottom),
            srcSize = IntSize(slices.left, slices.bottom),
            dstOffset = IntOffset(0, area.height - slices.bottom),
            alpha = alpha,
            colorFilter = colorFilter
        )
        repeat(hTimes) {
            scope.drawSlice(
                image,
                srcOffset = IntOffset(slices.left, image.height - slices.bottom),
                srcSize = IntSize(hMiddleSize, slices.bottom),
                dstOffset = IntOffset(slices.left + it * hMiddleSize, area.height - slices.bottom),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        if (hExtra > 0) {
            scope.drawSlice(
                image,
                srcOffset = IntOffset(slices.left, image.height - slices.bottom),
                srcSize = IntSize(hExtra, slices.bottom),
                dstOffset = IntOffset(area.width - slices.right - hExtra, area.height - slices.bottom),
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        scope.drawSlice(
            image,
            srcOffset = IntOffset(image.width - slices.right, image.height - slices.bottom),
            srcSize = IntSize(slices.right, slices.bottom),
            dstOffset = IntOffset(area.width - slices.right, area.height - slices.bottom),
            alpha = alpha,
            colorFilter = colorFilter
        )
    }
}

class ImageSlicePainter(
    private val imageSlice: ImageSlice,
    private val scale: Float
) : Painter() {

    init {
        validateSize(imageSlice.slices)
    }

    private var alpha: Float = 1.0f

    private var colorFilter: ColorFilter? = null

    override fun DrawScope.onDraw() {
        imageSlice.draw(this, alpha, colorFilter)
    }

    /**
     * Return the dimension of the underlying [ImageBitmap] as it's intrinsic width and height
     */
    override val intrinsicSize: Size get() = IntSize(imageSlice.image.width, imageSlice.image.height).toSize()

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    private fun validateSize(slices: ImageSliceValues) {
        require(
            slices.top >= 0 &&
                slices.bottom >= 0 &&
                slices.left >= 0 &&
                slices.right >= 0 &&
                slices.horizontal <= imageSlice.image.width &&
                slices.vertical <= imageSlice.image.height
        )
    }
}

private fun DrawScope.drawSlice(
    bitmap: ImageBitmap,
    srcOffset: IntOffset,
    srcSize: IntSize,
    dstOffset: IntOffset,
    dstSize: IntSize = srcSize,
    alpha: Float,
    colorFilter: ColorFilter?
) {
    drawImage(bitmap, srcOffset, srcSize, dstOffset, dstSize, alpha = alpha, colorFilter = colorFilter)
}
