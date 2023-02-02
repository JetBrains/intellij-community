package org.jetbrains.jewel.shape

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

class QuadRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize
) : CornerBasedShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart
) {

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        QuadRoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart
        )

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline = if (topStart + topEnd + bottomEnd + bottomStart == 0.0f) {
        Outline.Rectangle(size.toRect())
    } else {
        Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = CornerRadius(if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd),
                topRight = CornerRadius(if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart),
                bottomRight = CornerRadius(if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart),
                bottomLeft = CornerRadius(if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd)
            )
        )
    }

    override fun toString(): String {
        return "QuadRoundedCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = " +
            "$bottomEnd, bottomStart = $bottomStart)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuadRoundedCornerShape) return false

        if (topStart != other.topStart) return false
        if (topEnd != other.topEnd) return false
        if (bottomEnd != other.bottomEnd) return false
        if (bottomStart != other.bottomStart) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
    }
}

/**
 * Creates [QuadRoundedCornerShape] with the same size applied for all four corners.
 * @param corner [CornerSize] to apply.
 */
fun QuadRoundedCornerShape(corner: CornerSize) =
    QuadRoundedCornerShape(corner, corner, corner, corner)

/**
 * Creates [QuadRoundedCornerShape] with the same size applied for all four corners.
 * @param size Size in [Dp] to apply.
 */
fun QuadRoundedCornerShape(size: Dp) = QuadRoundedCornerShape(CornerSize(size))

/**
 * Creates [QuadRoundedCornerShape] with the same size applied for all four corners.
 * @param size Size in pixels to apply.
 */
fun QuadRoundedCornerShape(size: Float) = QuadRoundedCornerShape(CornerSize(size))

/**
 * Creates [QuadRoundedCornerShape] with the same size applied for all four corners.
 * @param percent Size in percents to apply.
 */
fun QuadRoundedCornerShape(percent: Int) =
    QuadRoundedCornerShape(CornerSize(percent))

/**
 * Creates [QuadRoundedCornerShape] with sizes defined in [Dp].
 */
fun QuadRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp
) = QuadRoundedCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart)
)

/**
 * Creates [QuadRoundedCornerShape] with sizes defined in pixels.
 */
fun QuadRoundedCornerShape(
    topStart: Float = 0.0f,
    topEnd: Float = 0.0f,
    bottomEnd: Float = 0.0f,
    bottomStart: Float = 0.0f
) = QuadRoundedCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart)
)

/**
 * Creates [QuadRoundedCornerShape] with sizes defined in percents of the shape's smaller side.
 *
 * @param topStartPercent The top start corner radius as a percentage of the smaller side, with a
 * range of 0 - 100.
 * @param topEndPercent The top end corner radius as a percentage of the smaller side, with a
 * range of 0 - 100.
 * @param bottomEndPercent The bottom end corner radius as a percentage of the smaller side,
 * with a range of 0 - 100.
 * @param bottomStartPercent The bottom start corner radius as a percentage of the smaller side,
 * with a range of 0 - 100.
 */
fun QuadRoundedCornerShape(
    /*@IntRange(from = 0, to = 100)*/
    topStartPercent: Int = 0,
    /*@IntRange(from = 0, to = 100)*/
    topEndPercent: Int = 0,
    /*@IntRange(from = 0, to = 100)*/
    bottomEndPercent: Int = 0,
    /*@IntRange(from = 0, to = 100)*/
    bottomStartPercent: Int = 0
) = QuadRoundedCornerShape(
    topStart = CornerSize(topStartPercent),
    topEnd = CornerSize(topEndPercent),
    bottomEnd = CornerSize(bottomEndPercent),
    bottomStart = CornerSize(bottomStartPercent)
)

fun Path.addQuadRoundRect(roundRect: RoundRect) {
    val rrect = roundRect.normalized()
    moveTo(rrect.right - rrect.topRightCornerRadius.x, rrect.top)
    quadraticBezierTo(rrect.right, rrect.top, rrect.right, rrect.top + rrect.topRightCornerRadius.y)
    lineTo(rrect.right, rrect.bottom - rrect.bottomRightCornerRadius.y)
    quadraticBezierTo(rrect.right, rrect.bottom, rrect.right - rrect.bottomRightCornerRadius.x, rrect.bottom)
    lineTo(rrect.left + rrect.bottomLeftCornerRadius.x, rrect.bottom)
    quadraticBezierTo(rrect.left, rrect.bottom, rrect.left, rrect.bottom - rrect.bottomLeftCornerRadius.y)
    lineTo(rrect.left, rrect.top + rrect.topLeftCornerRadius.y)
    quadraticBezierTo(rrect.left, rrect.top, rrect.left + rrect.topLeftCornerRadius.x, rrect.top)
    close()
}

private fun RoundRect.normalized(): RoundRect = copy(
    topRightCornerRadius = topRightCornerRadius.normalized(this),
    topLeftCornerRadius = topLeftCornerRadius.normalized(this),
    bottomRightCornerRadius = bottomRightCornerRadius.normalized(this),
    bottomLeftCornerRadius = bottomLeftCornerRadius.normalized(this)
)

private fun CornerRadius.normalized(roundRect: RoundRect): CornerRadius = copy(
    x = max(min(x, roundRect.width / 2), 0f),
    y = max(min(y, roundRect.height / 2), 0f)
)
