@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.scrollindicator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.awaitWindow
import org.jetbrains.jewel.intui.standalone.stopApplicationWindow
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.scrollIndicator
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
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

        val offset = AtomicInteger(0)
        val app = SpectreTestApplication { ScrollIndicatorScreen(onScrollOffsetChange = offset::set) }
        app.start()
        try {
            val window = app.awaitWindow()
            val driver = RobotDriver.synthetic(window)
            val automator = ComposeAutomator.inProcess(driver)

            val list = automator.waitForNode(tag = SCROLLABLE_TAG)
            assertEquals(0, offset.get(), "The content should start unscrolled")

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
            assertTrue(
                pollUntil { offset.get() > 0 },
                "The wheel over the reserved lane should scroll the content forward",
            )

            driver.scrollWheel(laneX, laneY, wheelClicks = -3)
            assertTrue(
                pollUntil { offset.get() == 0 },
                "Wheeling back over the reserved lane should return the content to the top",
            )
        } finally {
            app.stop()
        }
    }

    @Test
    fun `the wheel over the horizontal reserved lane scrolls in RTL`(): Unit = runSpectreTest {
        assumeTrue(hostOs == OS.MacOS, "The indicator only reserves a lane on macOS")

        val offset = AtomicInteger(0)
        val app = SpectreTestApplication { RtlHorizontalScrollIndicatorScreen(onScrollOffsetChange = offset::set) }
        app.start()
        try {
            val window = app.awaitWindow()
            val driver = RobotDriver.synthetic(window)
            val automator = ComposeAutomator.inProcess(driver)

            val row = automator.waitForNode(tag = SCROLLABLE_TAG)
            assertEquals(0, offset.get(), "The RTL row should start unscrolled")

            val bounds = row.boundsOnScreen
            val laneX = bounds.x + bounds.width / 2
            val laneY = bounds.y + bounds.height - 2
            automator.moveTo(laneX, laneY)
            // In RTL, ScrollableDefaults.reverseDirection flips horizontal wheel deltas, so negative wheel
            // clicks advance scrollOffset from 0 and positive clicks return it to 0.
            driver.scrollWheel(laneX, laneY, wheelClicks = -3)

            assertTrue(
                pollUntil { offset.get() > 0 },
                "The wheel over the bottom reserved lane should scroll the RTL row forward",
            )

            driver.scrollWheel(laneX, laneY, wheelClicks = 3)
            assertTrue(
                pollUntil { offset.get() == 0 },
                "Wheeling the opposite way over the bottom reserved lane should return the RTL row to the start",
            )
        } finally {
            app.stop()
        }
    }

    @Test
    fun `clicking the track pages the content down and back up`(): Unit = runSpectreTest {
        val offset = AtomicInteger(0)
        val app = SpectreTestApplication { ScrollIndicatorScreen(onScrollOffsetChange = offset::set) }
        app.start()
        try {
            val window = app.awaitWindow()
            val driver = RobotDriver.synthetic(window)
            val automator = ComposeAutomator.inProcess(driver)

            val list = automator.waitForNode(tag = SCROLLABLE_TAG)
            assertEquals(0, offset.get(), "The content should start unscrolled")

            val bounds = list.boundsOnScreen
            val trackX = bounds.x + bounds.width - 5
            val trackBottomY = bounds.y + bounds.height - 12
            val trackTopY = bounds.y + 12

            driver.click(trackX, trackBottomY)
            assertTrue(pollUntil { offset.get() > 0 }, "Clicking the lower track should page the content down")

            val pagedDownOffset = offset.get()
            driver.click(trackX, trackTopY)
            assertTrue(
                pollUntil { offset.get() < pagedDownOffset },
                "Clicking the upper track should page the content back up",
            )
        } finally {
            app.stop()
        }
    }

    @Test
    fun `overlay indicator passes clicks through when collapsed and intercepts after expand delay`(): Unit =
        runSpectreTest {
            val offset = AtomicInteger(0)
            val buttonClicks = AtomicInteger(0)
            val app = SpectreTestApplication {
                OverlayScrollIndicatorScreen(
                    onScrollOffsetChange = offset::set,
                    onButtonClick = buttonClicks::incrementAndGet,
                )
            }
            app.start()
            try {
                val window = app.awaitWindow()
                val driver = RobotDriver.synthetic(window)
                val automator = ComposeAutomator.inProcess(driver)

                val list = automator.waitForNode(tag = SCROLLABLE_TAG)
                val bounds = list.boundsOnScreen
                val trackX = bounds.x + bounds.width - 5
                val trackBottomY = bounds.y + bounds.height - 20
                val thumbTopY = bounds.y + 16

                // While collapsed, clicking the lower track region must pass through to the content underneath.
                driver.click(trackX, trackBottomY)
                assertTrue(
                    pollUntil { buttonClicks.get() == 1 },
                    "Clicking under the collapsed overlay track should reach the child button",
                )
                assertEquals(0, offset.get(), "Collapsed track click must not scroll the content")

                // Hovering the collapsed thumb and waiting past expandDelay expands the track so clicks page instead.
                automator.moveTo(trackX, thumbTopY)
                delay(700.milliseconds)
                driver.click(trackX, trackBottomY)

                assertTrue(
                    pollUntil { offset.get() > 0 },
                    "Clicking the expanded overlay track after expandDelay should page the content",
                )
                assertEquals(
                    1,
                    buttonClicks.get(),
                    "Expanded overlay track click must not reach the child button underneath",
                )
            } finally {
                app.stop()
            }
        }
}

private suspend fun pollUntil(condition: () -> Boolean): Boolean =
    (1..50).any {
        if (condition()) return@any true
        delay(100.milliseconds)
        false
    }

private class SpectreTestApplication(private val content: @Composable () -> Unit) {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)
    private val appThread = AtomicReference<Thread?>(null)

    fun start() {
        val t =
            thread(name = "spectre-scroll-indicator-window", isDaemon = true) {
                application(exitProcessOnExit = false) {
                    exitApplication.set(::exitApplication)
                    JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel Spectre scroll indicator test") {
                        this@SpectreTestApplication.window.compareAndSet(null, window)
                        IntUiTheme { content() }
                    }
                }
            }
        appThread.set(t)
    }

    fun stop() {
        stopApplicationWindow(exitApplication, window, appThread)
    }

    suspend fun awaitWindow(): ComposeWindow = window.awaitWindow()
}

@Composable
private fun ScrollIndicatorScreen(onScrollOffsetChange: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.value }.collect(onScrollOffsetChange) }

    // AlwaysVisible is what makes the modifier reserve a lane on macOS.
    val baseStyle = JewelTheme.scrollbarStyle
    val style =
        remember(baseStyle) {
            ScrollbarStyle(
                colors = baseStyle.colors,
                metrics = baseStyle.metrics,
                trackClickBehavior = TrackClickBehavior.NextPage,
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

@Composable
private fun RtlHorizontalScrollIndicatorScreen(onScrollOffsetChange: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.value }.collect(onScrollOffsetChange) }

    val baseStyle = JewelTheme.scrollbarStyle
    val style =
        remember(baseStyle) {
            ScrollbarStyle(
                colors = baseStyle.colors,
                metrics = baseStyle.metrics,
                trackClickBehavior = TrackClickBehavior.NextPage,
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier.fillMaxSize()
                .background(Color(0xFFF7F8FA))
                .testTag(SCROLLABLE_TAG)
                .scrollIndicator(scrollState, Orientation.Horizontal, style = style)
        ) {
            Row(Modifier.fillMaxHeight().horizontalScroll(scrollState)) {
                repeat(60) { index ->
                    Text("Col $index", Modifier.width(80.dp).fillMaxHeight().padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun OverlayScrollIndicatorScreen(onScrollOffsetChange: (Int) -> Unit, onButtonClick: () -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.value }.collect(onScrollOffsetChange) }

    val baseStyle = JewelTheme.scrollbarStyle
    val style =
        remember(baseStyle) {
            ScrollbarStyle(
                colors = baseStyle.colors,
                metrics = baseStyle.metrics,
                trackClickBehavior = TrackClickBehavior.NextPage,
                scrollbarVisibility =
                    ScrollbarVisibility.WhenScrolling(
                        trackThickness = 7.dp,
                        trackThicknessExpanded = 14.dp,
                        trackPadding = PaddingValues(1.dp),
                        trackPaddingWithBorder = PaddingValues(1.dp),
                        trackColorAnimationDuration = Duration.ZERO,
                        expandAnimationDuration = Duration.ZERO,
                        thumbColorAnimationDuration = Duration.ZERO,
                        lingerDuration = 5.seconds,
                        expandDelay = 500.milliseconds,
                        collapseDelay = 300.milliseconds,
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
        Box(Modifier.align(Alignment.BottomEnd).width(32.dp).height(60.dp).clickable(onClick = onButtonClick))
    }
}

private const val SCROLLABLE_TAG = "spectre.scrollIndicator.scrollable"
