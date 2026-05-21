// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.jewel.window.utils.clientRegion
import org.junit.Rule

public class TitleBarInfoTest {
    @get:Rule public val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    public fun `client regions survive recomposition triggered by title change`() {
        val titleBarInfo = TitleBarInfo("Window Title", null)

        composeRule.setContent {
            CompositionLocalProvider(LocalTitleBarInfo provides titleBarInfo) {
                Box(Modifier.size(200.dp).clientRegion("switch_theme")) { BasicText(titleBarInfo.title) }
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)

        titleBarInfo.title = "Window Title Changed"

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)
    }

    @Test
    public fun `client regions survive recomposition triggered by icon change`() {
        val titleBarInfo = TitleBarInfo("", null)

        composeRule.setContent {
            CompositionLocalProvider(LocalTitleBarInfo provides titleBarInfo) {
                Box(Modifier.size(40.dp).clientRegion("switch_theme")) {
                    val painter = titleBarInfo.icon
                    if (painter != null) {
                        Image(painter = painter, contentDescription = null)
                    }
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)

        titleBarInfo.icon = ColorPainter(Color.Red)

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)
    }

    @Test
    public fun `client region rect updates after layout change`() {
        val titleBarInfo = TitleBarInfo("", null)
        val buttonSize = mutableStateOf(40.dp)
        val xOffset = mutableStateOf(0.dp)

        composeRule.setContent {
            CompositionLocalProvider(LocalTitleBarInfo provides titleBarInfo) {
                Box(
                    Modifier.size(buttonSize.value).padding(5.dp).offset(x = xOffset.value).clientRegion("switch_theme")
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)
        val oldRect = titleBarInfo.clientRegions["switch_theme"]

        buttonSize.value = 60.dp
        xOffset.value = 30.dp

        composeRule.waitUntil(timeoutMillis = 1000, conditionDescription = "Waiting for client region rect change") {
            titleBarInfo.clientRegions["switch_theme"] != oldRect
        }

        assertNotEquals(titleBarInfo.clientRegions["switch_theme"], oldRect)
        assertEquals(1, titleBarInfo.clientRegions.size)
    }

    @Test
    public fun `client region is removed on detach`() {
        val titleBarInfo = TitleBarInfo("", null)
        val showBox = mutableStateOf(true)

        composeRule.setContent {
            CompositionLocalProvider(LocalTitleBarInfo provides titleBarInfo) {
                if (showBox.value) {
                    Box(Modifier.size(40.dp).clientRegion("switch_theme"))
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, titleBarInfo.clientRegions.size)

        showBox.value = false

        composeRule.waitForIdle()
        assertEquals(0, titleBarInfo.clientRegions.size)
    }

    @Test
    public fun `client region state is independent between multiple titleBarInfo instances`() {
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
