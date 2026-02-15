// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.PopupAdStyle
import org.jetbrains.jewel.ui.theme.popupAdStyle

/**
 * A composable that provides a styled container for supplementary content at the bottom of popups, similar to
 * IntelliJ's
 * [`AbstractPopup.setAdText(String)`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/popup/AbstractPopup.java#L464)
 *
 * This component is typically used to display hints, shortcuts, or additional information in popup menus and combo
 * boxes.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * @param modifier The modifier to be applied to the container.
 * @param style The style to apply to the container. Defaults to the theme's popup ad style.
 * @param content The content to display inside the container.
 */
@Composable
public fun PopupAd(
    modifier: Modifier = Modifier,
    style: PopupAdStyle = JewelTheme.popupAdStyle,
    content: @Composable () -> Unit,
) {
    val colors = style.colors
    val metrics = style.metrics

    Box(
        modifier =
            modifier
                .background(colors.background)
                .defaultMinSize(minHeight = metrics.minHeight)
                .padding(metrics.padding)
    ) {
        content()
    }
}
