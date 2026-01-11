package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.popupShadowAndBorder
import org.jetbrains.jewel.ui.theme.popupContainerStyle

@Composable
public fun PopupContainer(
    onDismissRequest: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    style: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = LocalDensity.current,
        ),
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = popupProperties,
        cornerSize = style.metrics.cornerSize,
    ) {
        OverrideDarkMode(style.isDark) {
            val colors = style.colors
            val popupShape = RoundedCornerShape(style.metrics.cornerSize)

            Box(
                modifier =
                    modifier
                        .popupShadowAndBorder(
                            shape = popupShape,
                            shadowSize = style.metrics.shadowSize,
                            shadowColor = colors.shadow,
                            borderWidth = style.metrics.borderWidth,
                            borderColor = colors.border,
                        )
                        .background(colors.background, popupShape)
                        .clip(popupShape)
            ) {
                content()
            }
        }
    }
}
