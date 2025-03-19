package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarColors
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarMetrics
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle

internal fun readHorizontalProgressBarStyle() =
    HorizontalProgressBarStyle(
        colors =
            HorizontalProgressBarColors(
                track = retrieveColorOrUnspecified("ProgressBar.trackColor"),
                progress = retrieveColorOrUnspecified("ProgressBar.progressColor"),
                indeterminateBase = retrieveColorOrUnspecified("ProgressBar.indeterminateStartColor"),
                indeterminateHighlight = retrieveColorOrUnspecified("ProgressBar.indeterminateEndColor"),
            ),
        metrics =
            HorizontalProgressBarMetrics(
                cornerSize = CornerSize(100),
                minHeight = 4.dp, // See DarculaProgressBarUI.DEFAULT_WIDTH
                // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT,
                // DarculaProgressBarUI.REPAINT_INTERVAL_DEFAULT,
                // and the "step" constant in DarculaProgressBarUI#paintIndeterminate
                indeterminateHighlightWidth = (800 / 50 * 6).dp,
            ),
        indeterminateCycleDuration = 800.milliseconds, // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT
    )
