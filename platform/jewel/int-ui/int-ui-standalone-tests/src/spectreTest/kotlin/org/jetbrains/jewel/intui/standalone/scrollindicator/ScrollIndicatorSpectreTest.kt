@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.scrollindicator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.scrollIndicator
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

// These assertions are about what a real window does with real OS input. Under AlwaysVisible on macOS the indicator
// reserves a lane that is measured out of the scrollable content, so the wheel over that lane lands on a region no
// scrollable owns. A ComposeUiTest cannot catch a regression here, because it never reproduces the native wheel event
// that a reserved lane has to forward.
class ScrollIndicatorSpectreTest {
    @Test
    fun `the wheel over the reserved lane scrolls the content`(): Unit = runSpectreTest {
        assumeTrue(hostOs == OS.MacOS, "The indicator only reserves a lane on macOS")

        val app = SpectreTestApplication()
        app.start()
        try {
            val window = app.awaitWindow()
            val driver = RobotDriver.synthetic(window)
            val automator = ComposeAutomator.inProcess(driver)

            val list = automator.waitForNode(tag = SCROLLABLE_TAG)
            assertTrue(app.scrollOffset == 0, "The content should start unscrolled")

            // Aim at the reserved lane itself: a couple of pixels in from the trailing edge, which is
            // outside the scrollable content but inside the node the modifier decorates.
            val bounds = list.boundsOnScreen
            val laneX = bounds.x + bounds.width - 2
            val laneY = bounds.y + bounds.height / 2
            automator.moveTo(laneX, laneY)
            // ComposeAutomator.scrollWheel centres on a node; the lane needs an exact point.
            driver.scrollWheel(laneX, laneY, wheelClicks = 3)

            // The window is an accessory (apple.awt.UIElement), so it cannot be screen-sampled;
            // poll the scroll state instead, the same way the other Spectre tests poll semantics.
            val scrolled =
                (1..50).any {
                    if (app.scrollOffset > 0) return@any true
                    delay(100.milliseconds)
                    false
                }
            assertTrue(scrolled, "The wheel over the reserved lane should scroll the content")
        } finally {
            app.stop()
        }
    }
}

private class SpectreTestApplication {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)
    private val offset = AtomicInteger(0)

    /** The live scroll position, so assertions can cross-check the screen against the state. */
    val scrollOffset: Int
        get() = offset.get()

    fun start() {
        thread(name = "spectre-scroll-indicator-window", isDaemon = true) {
            application(exitProcessOnExit = false) {
                exitApplication.set(::exitApplication)
                JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel Spectre scroll indicator test") {
                    this@SpectreTestApplication.window.compareAndSet(null, window)
                    IntUiTheme { ScrollIndicatorScreen(onScrollOffsetChange = offset::set) }
                }
            }
        }
    }

    fun stop() {
        exitApplication.get()?.invoke()
    }

    suspend fun awaitWindow(): ComposeWindow {
        repeat(100) {
            window.get()?.let {
                return it
            }
            delay(100.milliseconds)
        }
        error("The Compose test window was not created")
    }
}

@Composable
private fun ScrollIndicatorScreen(onScrollOffsetChange: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    onScrollOffsetChange(scrollState.value)

    // AlwaysVisible is what makes the modifier reserve a lane, which is the whole point of this test.
    val baseStyle = JewelTheme.scrollbarStyle
    val style =
        remember(baseStyle) {
            ScrollbarStyle(
                colors = baseStyle.colors,
                metrics = baseStyle.metrics,
                trackClickBehavior = baseStyle.trackClickBehavior,
                scrollbarVisibility =
                    ScrollbarVisibility.AlwaysVisible(
                        trackThickness = 14.dp,
                        trackPadding = PaddingValues(2.dp),
                        trackPaddingWithBorder = PaddingValues(2.dp),
                        thumbColorAnimationDuration = Duration.ZERO,
                        trackColorAnimationDuration = Duration.ZERO,
                        scrollbarBackgroundColorLight = Color.Transparent,
                        scrollbarBackgroundColorDark = Color.Transparent,
                    ),
            )
        }

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .testTag(SCROLLABLE_TAG)
            .scrollIndicator(scrollState, style = style)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            repeat(60) { index ->
                Text("Item $index", Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp))
            }
        }
    }
}

private const val SCROLLABLE_TAG = "spectre.scrollIndicator.scrollable"
