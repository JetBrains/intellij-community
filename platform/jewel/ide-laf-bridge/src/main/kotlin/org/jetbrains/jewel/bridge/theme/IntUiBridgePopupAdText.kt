package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ui.NewUiValue
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValuesOrNull
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.PopupAdTextColors
import org.jetbrains.jewel.ui.component.styling.PopupAdTextMetrics
import org.jetbrains.jewel.ui.component.styling.PopupAdTextStyle

internal fun readPopupAdTextStyle(): PopupAdTextStyle {
    // Get colors from LaF following JBUI.CurrentTheme.Advertiser pattern
    // Uses Popup.Advertiser.* keys with fallback to SearchEverywhere.Advertiser.*
    val foregroundColor =
        retrieveColorOrUnspecified("Popup.Advertiser.foreground").takeOrElse {
            retrieveColorOrUnspecified("SearchEverywhere.Advertiser.foreground").takeOrElse {
                JBUI.CurrentTheme.Advertiser.foreground().toComposeColor()
            }
        }

    val backgroundColor =
        retrieveColorOrUnspecified("Popup.Advertiser.background").takeOrElse {
            retrieveColorOrUnspecified("SearchEverywhere.Advertiser.background").takeOrElse {
                JBUI.CurrentTheme.Advertiser.background().toComposeColor()
            }
        }

    val colors = PopupAdTextColors(foreground = foregroundColor, background = backgroundColor)

    // Get border insets from LaF following JBUI.CurrentTheme.Advertiser.borderInsets() pattern
    // For new UI: insets(6, 20), for legacy UI: insets(5, 10, 5, 15)
    val borderInsets =
        retrieveInsetsAsPaddingValuesOrNull("Popup.Advertiser.borderInsets")
            ?: if (NewUiValue.isEnabled()) {
                // New UI default
                PaddingValues(horizontal = 20.dp, vertical = 6.dp)
            } else {
                // Legacy UI default
                PaddingValues(start = 10.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
            }

    // Get font size offset from LaF following JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET
    // Default is -2 (making font slightly smaller)
    val fontSizeOffset = uiDefaults["Popup.Advertiser.fontSizeOffset"] as? Int ?: -2

    val baseFontSize = uiDefaults.getFont("Label.font")?.size ?: 13
    val fontSize = (baseFontSize + fontSizeOffset).coerceAtLeast(9)

    val metrics = PopupAdTextMetrics(padding = borderInsets, minHeight = 20.dp, spacerHeight = 4.dp)

    val textStyle = TextStyle(fontSize = fontSize.sp, fontFamily = FontFamily.Default)

    return PopupAdTextStyle(colors = colors, metrics = metrics, textStyle = textStyle)
}
