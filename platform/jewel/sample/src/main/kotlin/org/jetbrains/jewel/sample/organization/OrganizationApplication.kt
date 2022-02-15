package org.jetbrains.jewel.sample.organization

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.components.Divider
import org.jetbrains.jewel.theme.toolbox.components.Tab
import org.jetbrains.jewel.theme.toolbox.components.TabColumn
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.components.rememberTabContainerState
import org.jetbrains.jewel.theme.toolbox.metrics
import org.jetbrains.jewel.theme.toolbox.styles.frame
import org.jetbrains.jewel.theme.toolbox.typography

@Composable
fun OrganizationApplication() {
    val backgroundColor = Styles.frame.appearance(Unit).backgroundColor
    Row(modifier = Modifier.background(backgroundColor)) {
        val page = rememberTabContainerState("Dashboard")
        Column {
            val columnWidth = Styles.metrics.base * 30
            Column(Modifier.width(columnWidth)) {
                Box(Modifier.size(columnWidth, 128.dp).padding(Styles.metrics.largePadding)) {
                    Image(
                        painterResource("organization/toolbox.svg"),
                        "toolbox",
                        modifier = Modifier.size(Styles.metrics.base * 20)
                    )
                }
                Divider()
            }
            TabColumn(
                page,
                modifier = Modifier.fillMaxHeight().width(columnWidth).padding(Styles.metrics.smallPadding),
                verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding)
            ) {
                Tab("Dashboard") {
                    Section("dashboard", "Dashboard")
                }
                Tab("Projects") {
                    Section("projects", "Projects")
                }
                Tab("Teams") {
                    Section("teams", "Teams")
                }
                Spacer(Modifier.weight(1f))
                Tab("Notifications") {
                    Section("notifications", "Notifications")
                }
                Tab("Account") {
                    Section("avatar", "Ivan Ivanov")
                }
            }
        }
        Divider(orientation = Orientation.Vertical)
        Column(modifier = Modifier.fillMaxSize()) {
            when (page.selectedKey) {
                "Dashboard" -> {
                    TitlePanel("Dashboard")
                }
                "Projects" -> {
                    TitlePanel("Projects")
                }
                "Teams" -> {
                    TitlePanel("Teams")
                }
            }
        }
    }
}

@Composable
private fun Section(icon: String, caption: String) {
    val style = LocalTextStyle.current
    Image(
        painterResource("organization/$icon.svg"),
        icon,
        modifier = Modifier.size(Styles.metrics.largePadding),
        colorFilter = ColorFilter.tint(style.color)
    )
    Spacer(Modifier.width(Styles.metrics.smallPadding))
    Text(caption, Modifier.padding(top = 3.dp))
}

@Composable
private fun TitlePanel(title: String) {
    Box(Modifier.height(128.dp).padding(Styles.metrics.mediumPadding)) {
        Text(title, style = Styles.typography.subtitle)
    }
    Divider()
}
