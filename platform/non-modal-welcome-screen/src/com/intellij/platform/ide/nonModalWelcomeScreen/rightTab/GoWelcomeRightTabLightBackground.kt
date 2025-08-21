package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GoWelcomeRightTabLightBackground: ImageVector
  get() {
    if (_GoWelcomeRightTabLightBackground != null) {
      return _GoWelcomeRightTabLightBackground!!
    }
    _GoWelcomeRightTabLightBackground = ImageVector.Builder(
      name = "GoWelcomeRightTabLightBackground",
      defaultWidth = 1094.dp,
      defaultHeight = 878.dp,
      viewportWidth = 1094f,
      viewportHeight = 878f
    ).apply {
      group(
        clipPathData = PathData {
          moveTo(0f, 0f)
          horizontalLineToRelative(1094f)
          verticalLineToRelative(1117f)
          horizontalLineToRelative(-1094f)
          close()
        }
      ) {
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF00D886),
              1f to Color(0x00F7F8FA)
            ),
            center = Offset(396.8f, 1.6f),
            radius = 348.8f
          ),
          fillAlpha = 0.6f,
          strokeAlpha = 0.6f
        ) {
          moveTo(287f, 573.4f)
          arcToRelative(528.7f, 300f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, -1057.4f)
          arcToRelative(528.7f, 300f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, 1057.4f)
          close()
        }
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF7256FF),
              1f to Color(0x00F7F8FA)
            ),
            center = Offset(462.4f, -281.9f),
            radius = 579.3f
          ),
          fillAlpha = 0.7f,
          strokeAlpha = 0.7f
        ) {
          moveTo(493f, -579f)
          curveTo(665.9f, -579f, 806f, -363.2f, 806f, -97f)
          curveTo(806f, 169.2f, 665.9f, 385f, 493f, 385f)
          curveTo(320.1f, 385f, 180f, 169.2f, 180f, -97f)
          curveTo(180f, -363.2f, 320.1f, -579f, 493f, -579f)
          close()
        }
        path(
          fill = Brush.radialGradient(
            colorStops = arrayOf(
              0f to Color(0xFF7256FF),
              1f to Color(0x00F7F8FA)
            ),
            center = Offset(727.6f, -157.1f),
            radius = 501.6f
          ),
          fillAlpha = 0.6f,
          strokeAlpha = 0.6f
        ) {
          moveTo(765.5f, 415f)
          arcToRelative(413.5f, 387.5f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, -827f)
          arcToRelative(413.5f, 387.5f, 90f, isMoreThanHalf = true, isPositiveArc = false, 0f, 827f)
          close()
        }
      }
    }.build()

    return _GoWelcomeRightTabLightBackground!!
  }

@Suppress("ObjectPropertyName")
private var _GoWelcomeRightTabLightBackground: ImageVector? = null
