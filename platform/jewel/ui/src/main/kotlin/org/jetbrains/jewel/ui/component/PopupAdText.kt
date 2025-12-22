// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.PopupAdTextStyle
import org.jetbrains.jewel.ui.theme.popupAdTextStyle

/**
 * A composable that displays supplementary text at the bottom of popups, similar to IntelliJ's
 * [`AbstractPopup.setAdText(String)`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/popup/AbstractPopup.java#L464)
 *
 * This component is typically used to display hints, shortcuts, or additional information in popup menus and combo
 * boxes.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * @param text The text to display. If empty, the component is not rendered.
 * @param modifier The modifier to be applied to the component.
 * @param style The style to apply to the ad text. Defaults to the theme's popup ad text style.
 * @param textAlign The horizontal alignment of the text.
 */
@Composable
public fun PopupAdText(
    @Nls text: String,
    modifier: Modifier = Modifier,
    style: PopupAdTextStyle = JewelTheme.popupAdTextStyle,
    textAlign: TextAlign = TextAlign.Start,
) {
    val colors = style.colors
    val metrics = style.metrics

    Text(
        text = text,
        style = style.textStyle.copy(color = colors.foreground, textAlign = textAlign),
        modifier =
            modifier
                .background(colors.background)
                .defaultMinSize(minHeight = metrics.minHeight)
                .padding(metrics.padding),
    )
}
