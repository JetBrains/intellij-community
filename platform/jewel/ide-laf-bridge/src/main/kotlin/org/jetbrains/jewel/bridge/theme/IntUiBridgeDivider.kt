package org.jetbrains.jewel.bridge.theme

import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle

internal fun readDividerStyle() =
    DividerStyle(color = JBColor.border().toComposeColorOrUnspecified(), metrics = DividerMetrics.defaults())
