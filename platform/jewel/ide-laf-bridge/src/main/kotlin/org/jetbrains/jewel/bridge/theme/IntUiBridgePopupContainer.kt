package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.ui.component.styling.PopupContainerColors
import org.jetbrains.jewel.ui.component.styling.PopupContainerMetrics
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle

internal fun readPopupContainerStyle(): PopupContainerStyle {
    val colors =
        PopupContainerColors(
            background = retrieveColorOrUnspecified("PopupMenu.background"),
            border =
                retrieveColorOrUnspecified("Popup.borderColor").takeOrElse {
                    retrieveColorOrUnspecified("Popup.Border.color")
                },
            shadow = Color.Black.copy(alpha = .6f),
        )

    return PopupContainerStyle(
        isDark = isDark,
        colors = colors,
        metrics =
            PopupContainerMetrics(
                cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp),
                menuMargin = PaddingValues(),
                contentPadding = PaddingValues(),
                offset = DpOffset(0.dp, 2.dp),
                shadowSize = 12.dp,
                borderWidth = retrieveIntAsDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
            ),
    )
}
