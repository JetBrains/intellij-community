package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValuesOrNull
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.PopupAdColors
import org.jetbrains.jewel.ui.component.styling.PopupAdMetrics
import org.jetbrains.jewel.ui.component.styling.PopupAdStyle

internal fun readPopupAdStyle(): PopupAdStyle {
    val backgroundColor = JBUI.CurrentTheme.Advertiser.background().toComposeColor()
    val colors = PopupAdColors(background = backgroundColor)
    val borderInsets =
        retrieveInsetsAsPaddingValuesOrNull(JBUI.CurrentTheme.Advertiser.borderInsetsKey())
            ?: PaddingValues(horizontal = 20.dp, vertical = 6.dp)
    val metrics = PopupAdMetrics(padding = borderInsets, minHeight = 20.dp)

    return PopupAdStyle(colors = colors, metrics = metrics)
}
