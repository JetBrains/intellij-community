package org.jetbrains.jewel.ui.theme

import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@ExperimentalJewelApi
public fun TooltipStyle.zeroDelayNeverHide(): TooltipStyle =
    TooltipStyle(
        colors = colors,
        metrics =
            TooltipMetrics(
                contentPadding = metrics.contentPadding,
                showDelay = 0.milliseconds,
                cornerSize = metrics.cornerSize,
                borderWidth = metrics.borderWidth,
                shadowSize = metrics.shadowSize,
                placement = metrics.placement,
                regularDisappearDelay = metrics.regularDisappearDelay,
                fullDisappearDelay = metrics.fullDisappearDelay,
            ),
        autoHideBehavior = TooltipAutoHideBehavior.Never,
    )
