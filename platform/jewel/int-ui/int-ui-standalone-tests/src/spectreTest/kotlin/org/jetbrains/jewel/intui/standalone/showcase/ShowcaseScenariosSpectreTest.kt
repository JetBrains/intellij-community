@file:OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)

package org.jetbrains.jewel.intui.standalone.showcase

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.e2e.PopupScenarios
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.junit.jupiter.api.Test

/**
 * Runs the shared showcase scenarios against the standalone sample, where Jewel renders popups with `JDialogRenderer`.
 *
 * The IDE lane runs these same scenarios against the same showcase views, hosted by DevKit's dialog, where
 * `JBPopupRenderer` renders them instead. Sharing the scenarios is the point: a popup behaviour that only holds in one
 * host fails in the other, which is how the JEWEL-1396 fault escaped notice for so long.
 */
class ShowcaseScenariosSpectreTest {
    @Test
    fun `the showcase behaves in the standalone host`(): Unit = runSpectreTest {
        assertEquals(true, JewelFlags.useCustomPopupRenderer, "spectreTest must enable JDialogRenderer")

        val app = ShowcaseApplication()
        app.start()
        val failures = mutableListOf<String>()
        try {
            val automator =
                SpectreShowcaseAutomator(ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow())))
            PopupScenarios.runAll(automator, failures)
        } finally {
            app.stop()
        }

        assertEquals(emptyList(), failures, "The showcase misbehaved in the standalone host")
    }
}

/** The same showcase views the IDE hosts in its components showcase dialog. */
private class ShowcaseApplication {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)

    fun start() {
        thread(name = "spectre-showcase-window", isDaemon = true) {
            application(exitProcessOnExit = false) {
                exitApplication.set(::exitApplication)
                JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel showcase") {
                    this@ShowcaseApplication.window.compareAndSet(null, window)
                    IntUiTheme { Showcase() }
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
private fun Showcase() {
    val viewModel =
        ComponentsViewModel(
            alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default(),
            whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs(),
        )
    ComponentsView(
        viewModel = viewModel,
        toolbarButtonMetrics =
            IconButtonMetrics(
                minSize = DpSize(24.dp, 24.dp),
                cornerSize = CornerSize(4.dp),
                padding = PaddingValues(2.dp),
                borderWidth = 0.dp,
            ),
    )
}
