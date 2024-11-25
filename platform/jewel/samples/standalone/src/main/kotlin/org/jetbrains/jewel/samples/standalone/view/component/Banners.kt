package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ErrorBanner
import org.jetbrains.jewel.ui.component.InformationBanner
import org.jetbrains.jewel.ui.component.SuccessBanner
import org.jetbrains.jewel.ui.component.WarningBanner
import org.jetbrains.jewel.ui.theme.defaultBannerStyle

@Composable
internal fun Banners() {
    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
        InformationBanner(
            style = JewelTheme.defaultBannerStyle.information,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )
        Spacer(modifier = Modifier.height(8.dp))
        SuccessBanner(
            style = JewelTheme.defaultBannerStyle.success,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )
        Spacer(modifier = Modifier.height(8.dp))
        WarningBanner(
            style = JewelTheme.defaultBannerStyle.warning,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )
        Spacer(modifier = Modifier.height(8.dp))
        ErrorBanner(
            style = JewelTheme.defaultBannerStyle.error,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
        )
    }
}
