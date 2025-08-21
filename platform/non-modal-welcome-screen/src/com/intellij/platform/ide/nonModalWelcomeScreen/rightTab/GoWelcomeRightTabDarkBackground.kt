package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GoWelcomeRightTabDarkBackground: ImageVector
  get() {
    if (_GoWelcomeRightTabDarkBackground != null) {
      return _GoWelcomeRightTabDarkBackground!!
    }
    _GoWelcomeRightTabDarkBackground = ImageVector.Builder(
      name = "GoWelcomeRightTabDarkBackground",
      defaultWidth = 1094.dp,
      defaultHeight = 847.dp,
      viewportWidth = 1094f,
      viewportHeight = 847f
    ).apply {
      group(
        clipPathData = PathData {
          moveTo(0f, 0f)
          horizontalLineToRelative(1094f)
          verticalLineToRelative(847f)
          horizontalLineToRelative(-1094f)
          close()
        }
      ) {
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF00D886),
              1f to Color(0x0027282E)
            ),
            center = Offset(396.8f, 47.5f),
            radius = 337.6f
          ),
          fillAlpha = 0.3f,
          strokeAlpha = 0.3f
        ) {
          moveTo(287f, 481.1f)
          arcToRelative(400.9f, 300f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, -801.8f)
          arcToRelative(400.9f, 300f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, 801.8f)
          close()
        }
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF007DFE),
              1f to Color(0x0027282E)
            ),
            center = Offset(511f, 19f),
            radius = 437.4f
          ),
          fillAlpha = 0.4f,
          strokeAlpha = 0.4f
        ) {
          moveTo(511f, 456.3f)
          arcToRelative(437.3f, 375f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, -874.7f)
          arcToRelative(437.3f, 375f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, 874.7f)
          close()
        }
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF7256FF),
              1f to Color(0x0027282E)
            ),
            center = Offset(718f, -78.6f),
            radius = 447.9f
          ),
          fillAlpha = 0.5f,
          strokeAlpha = 0.5f
        ) {
          moveTo(762f, 425.7f)
          arcToRelative(364.5f, 450f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, -728.9f)
          arcToRelative(364.5f, 450f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, 728.9f)
          close()
        }
      }
    }.build()

    return _GoWelcomeRightTabDarkBackground!!
  }

@Suppress("ObjectPropertyName")
private var _GoWelcomeRightTabDarkBackground: ImageVector? = null
