// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.window

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.jewel.window.utils.clientRegion
import org.junit.Rule

class TitleBarInfoTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `client regions remain valid after window title change`() {
        val title = mutableStateOf("Window Title")
        var titleBarInfo = TitleBarInfo("", null)

        composeRule.setContent {
            IntUiTheme(
                theme = JewelTheme.lightThemeDefinition(),
                styling = ComponentStyling.default().decoratedWindow(titleBarStyle = TitleBarStyle.light()),
            ) {
                DecoratedWindow(
                    title = title.value,
                    onCloseRequest = {},
                    content = {
                        val info = LocalTitleBarInfo.current

                        LaunchedEffect(Unit) { titleBarInfo = info }

                        TitleBar(Modifier.newFullscreenControls()) {
                            DefaultButton(
                                onClick = {},
                                modifier = Modifier.size(40.dp).padding(5.dp).clientRegion("switch_theme"),
                                content = { Text("Theme") },
                            )
                        }
                    },
                )
            }
        }

        composeRule.waitForIdle()

        title.value = "Window Title Changed"

        composeRule.waitUntil(timeoutMillis = 1000, conditionDescription = "Waiting for title to be updated") {
            titleBarInfo.title == title.value
        }

        assertEquals("Window Title Changed", titleBarInfo.title)
        assertEquals(titleBarInfo.clientRegions.size, 1)
    }

    @Test
    fun `client regions remain valid after client region rect change`() {
        var titleBarInfo = TitleBarInfo("", null)
        val buttonSize = mutableStateOf(40.dp)
        val xOffset = mutableStateOf(0.dp)

        composeRule.setContent {
            IntUiTheme(
                theme = JewelTheme.lightThemeDefinition(),
                styling = ComponentStyling.default().decoratedWindow(titleBarStyle = TitleBarStyle.light()),
            ) {
                DecoratedWindow(
                    onCloseRequest = {},
                    content = {
                        val info = LocalTitleBarInfo.current

                        LaunchedEffect(Unit) { titleBarInfo = info }

                        TitleBar(Modifier.newFullscreenControls()) {
                            DefaultButton(
                                onClick = {},
                                modifier =
                                    Modifier.size(buttonSize.value)
                                        .padding(5.dp)
                                        .offset(x = xOffset.value)
                                        .clientRegion("switch_theme"),
                                content = { Text("Theme") },
                            )
                        }
                    },
                )
            }
        }

        composeRule.waitForIdle()

        val oldRect = titleBarInfo.clientRegions["switch_theme"]
        buttonSize.value = 60.dp
        xOffset.value = 30.dp

        composeRule.waitUntil(timeoutMillis = 1000, conditionDescription = "Waiting for client regin rect change") {
            titleBarInfo.clientRegions["switch_theme"] != oldRect
        }

        assertNotEquals(titleBarInfo.clientRegions["switch_theme"], oldRect)
        assertEquals(titleBarInfo.clientRegions.size, 1)
    }

    @Test
    fun `client regions remain valid after window icon change`() {
        val icon = mutableStateOf<Painter?>(null)
        var titleBarInfo = TitleBarInfo("", null)

        composeRule.setContent {
            IntUiTheme(
                theme = JewelTheme.lightThemeDefinition(),
                styling = ComponentStyling.default().decoratedWindow(titleBarStyle = TitleBarStyle.light()),
            ) {
                DecoratedWindow(
                    icon = icon.value,
                    onCloseRequest = {},
                    content = {
                        val info = LocalTitleBarInfo.current

                        LaunchedEffect(Unit) { titleBarInfo = info }

                        TitleBar(Modifier.newFullscreenControls()) {
                            DefaultButton(
                                onClick = {},
                                modifier = Modifier.size(40.dp).padding(5.dp).clientRegion("switch_theme"),
                                content = { Text("Theme") },
                            )
                        }
                    },
                )
            }
        }

        composeRule.waitForIdle()

        icon.value = ColorPainter(Color.Red)

        composeRule.waitUntil(timeoutMillis = 1000, conditionDescription = "Waiting for icon to be updated") {
            titleBarInfo.icon == icon.value
        }

        assertEquals(icon.value, titleBarInfo.icon)
        assertEquals(titleBarInfo.clientRegions.size, 1)
    }

    @Test
    fun `client region state is independent between multiple titleBarInfo instances`() {
        val titleBarInfo1 = TitleBarInfo(title = "Window 1", icon = null)
        val titleBarInfo2 = TitleBarInfo(title = "Window 2", icon = null)

        val region1 = Rect(offset = Offset(10f, 5f), size = Size(50f, 30f))
        val region2 = Rect(offset = Offset(20f, 10f), size = Size(60f, 40f))

        titleBarInfo1.clientRegions["button"] = region1
        titleBarInfo2.clientRegions["button"] = region2

        assertEquals(region1, titleBarInfo1.clientRegions["button"])
        assertEquals(region2, titleBarInfo2.clientRegions["button"])
        assertNotEquals(titleBarInfo1.clientRegions["button"], titleBarInfo2.clientRegions["button"])
    }
}
