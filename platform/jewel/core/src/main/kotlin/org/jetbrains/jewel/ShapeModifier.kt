package org.jetbrains.jewel

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.debugInspectorInfo
import org.jetbrains.jewel.modifiers.BorderAlignment
import org.jetbrains.jewel.modifiers.background
import org.jetbrains.jewel.modifiers.border

@Deprecated(
    "Use Modifier.border and Modifier.background instead",
    ReplaceWith(
        "border(BorderAlignment.Inside, shapeStroke.width, shapeStroke.brush, shape).background(fillColor, shape)",
        "org.jetbrains.jewel.modifiers.border",
        "org.jetbrains.jewel.modifiers.background"
    )
)
fun Modifier.shape(shape: Shape, shapeStroke: ShapeStroke<*>? = null, fillColor: Color = Color.Unspecified): Modifier =
    shape(shape, shapeStroke, fillColor.nullIfUnspecified()?.toBrush())

@Deprecated(
    "Use Modifier.border and Modifier.background instead",
    ReplaceWith(
        "border(BorderAlignment.Inside, shapeStroke.width, shapeStroke.brush, shape).background(fillBrush, shape)",
        "org.jetbrains.jewel.modifiers.border",
        "org.jetbrains.jewel.modifiers.background"
    )
)
fun Modifier.shape(shape: Shape, shapeStroke: ShapeStroke<*>? = null, fillBrush: Brush?): Modifier =
    composed(
        factory = {
            val backgroundModifier = if (fillBrush != null) {
                this.background(fillBrush, shape)
            } else {
                this
            }
            if (shapeStroke != null) {
                backgroundModifier.border(BorderAlignment.Inside, shapeStroke.width, shapeStroke.brush, shape)
            } else {
                backgroundModifier
            }
        },
        inspectorInfo = debugInspectorInfo {
            name = "shape"
            properties["stroke"] = shapeStroke
            properties["shape"] = shape
        }
    )

fun Color.toBrush() = SolidColor(this)

private fun Color.nullIfUnspecified() = takeIf { it != Color.Unspecified }
