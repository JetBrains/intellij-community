package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ErrorBanner
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.InformationBanner
import org.jetbrains.jewel.ui.component.SuccessBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.WarningBanner
import org.jetbrains.jewel.ui.theme.defaultBannerStyle

@Composable
internal fun Banners() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Default banner (aka editor banners)")

        InformationBanner(
            style = JewelTheme.defaultBannerStyle.information,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )

        SuccessBanner(
            style = JewelTheme.defaultBannerStyle.success,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )

        WarningBanner(
            style = JewelTheme.defaultBannerStyle.warning,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )

        ErrorBanner(
            style = JewelTheme.defaultBannerStyle.error,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )

        Spacer(Modifier.height(0.dp)) // The column's arrangement will add 8+8 dps of spacing

        GroupHeader("Inline banner")

        Text("Coming soon...", color = JewelTheme.globalColors.text.disabled)
    }
}
