@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox
import org.junit.jupiter.api.Test

// Headful, and deliberately not a jps_test: the app under test must keep a standalone-only runtime closure,
// with no IntelliJ Platform classes on the classpath. Runs in CI on any agent with a display:
//   bazel test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-spectre-tests
class CustomPopupRendererSpectreTest {
    @Test
    fun `escape closes a hovered combo box`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val app = SpectreTestApplication()
        app.start()
        try {
            val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

            automator.click(automator.waitForNode(tag = REGULAR_COMBO_TAG))
            automator.waitForNode(tag = REGULAR_POPUP_VISIBLE_TAG, text = "true")

            // The pointer remains inside the ComboBox after click, reproducing the hover path that
            // previously let the popup's JDialogRenderer consume Escape as a no-op dismissal.
            automator.pressKey(KeyEvent.VK_ESCAPE)
            automator.waitForNode(tag = REGULAR_POPUP_VISIBLE_TAG, text = "false")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `escape closes a focusable menu when the owner window receives the key`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication()
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                val menuButton = automator.waitForNode(tag = MENU_BUTTON_TAG)
                automator.click(menuButton)
                automator.waitForNode(tag = MENU_VISIBLE_TAG, text = "true")

                // Spectre routes synthetic key events to the window under its injected pointer. Move that pointer back
                // to the owner window to cover focusable popups that have not taken native focus yet.
                automator.moveTo(menuButton)
                automator.waitForIdle()

                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitForNode(tag = MENU_VISIBLE_TAG, text = "false")
            } finally {
                app.stop()
            }
        }

    @Test
    fun `escape prioritizes speed search when dismiss on lose focus is disabled`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication()
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                automator.click(automator.waitForNode(tag = SPEED_SEARCH_COMBO_TAG))
                automator.waitForNode(tag = SPEED_SEARCH_POPUP_VISIBLE_TAG, text = "true")
                automator.typeText("Alpha")
                automator.waitForNode(tag = SPEED_SEARCH_QUERY_TAG, text = "Alpha")
                automator.waitForNode(tag = SPEED_SEARCH_VISIBLE_TAG, text = "true")

                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitForNode(tag = SPEED_SEARCH_VISIBLE_TAG, text = "false")
                automator.waitForNode(tag = SPEED_SEARCH_POPUP_VISIBLE_TAG, text = "true")

                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitForNode(tag = SPEED_SEARCH_POPUP_VISIBLE_TAG, text = "false")
            } finally {
                app.stop()
            }
        }
}

private fun runSpectreTestWithCustomPopupRenderer(block: suspend CoroutineScope.() -> Unit): Unit = runSpectreTest {
    assertTrue(JewelFlags.useCustomPopupRenderer, "spectreTest must enable JDialogRenderer")
    block()
}

@Composable
private fun PopupEscapeScreen() {
    val regularPopupManager = remember { PopupManager() }
    var menuVisible by remember { mutableStateOf(false) }
    var speedSearchPopupVisible by remember { mutableStateOf(false) }
    val speedSearchState = rememberSpeedSearchState()

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = regularPopupManager.isPopupVisible.value.toString(),
            modifier = Modifier.testTag(REGULAR_POPUP_VISIBLE_TAG),
        )
        ComboBox(
            labelText = "Regular ComboBox",
            modifier = Modifier.testTag(REGULAR_COMBO_TAG).width(240.dp),
            popupManager = regularPopupManager,
        ) {
            Text("Regular popup content")
        }

        Text(text = menuVisible.toString(), modifier = Modifier.testTag(MENU_VISIBLE_TAG))
        Box {
            DefaultButton(onClick = { menuVisible = true }, modifier = Modifier.testTag(MENU_BUTTON_TAG)) {
                Text("Show menu")
            }

            if (menuVisible) {
                PopupMenu(
                    onDismissRequest = {
                        menuVisible = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                ) {
                    selectableItem(selected = false, onClick = {}) { Text("Menu item") }
                }
            }
        }

        Text(text = speedSearchPopupVisible.toString(), modifier = Modifier.testTag(SPEED_SEARCH_POPUP_VISIBLE_TAG))
        Text(text = speedSearchState.isVisible.toString(), modifier = Modifier.testTag(SPEED_SEARCH_VISIBLE_TAG))
        Text(text = speedSearchState.searchText, modifier = Modifier.testTag(SPEED_SEARCH_QUERY_TAG))
        SpeedSearchArea(state = speedSearchState, dismissOnLoseFocus = false) {
            SpeedSearchableComboBox(
                items = listOf("Alpha", "Beta", "Gamma"),
                selectedIndex = 0,
                onSelectedItemChange = {},
                modifier = Modifier.testTag(SPEED_SEARCH_COMBO_TAG).width(240.dp),
                onPopupVisibleChange = { speedSearchPopupVisible = it },
            )
        }
    }
}

private class SpectreTestApplication(private val content: @Composable () -> Unit = { PopupEscapeScreen() }) {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)

    fun start() {
        thread(name = "spectre-popup-test-window", isDaemon = true) {
            application(exitProcessOnExit = false) {
                exitApplication.set(::exitApplication)
                JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel Spectre popup test") {
                    this@SpectreTestApplication.window.compareAndSet(null, window)
                    IntUiTheme { content() }
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
            delay(100)
        }
        error("The Compose test window was not created")
    }
}

private const val REGULAR_COMBO_TAG = "spectre.regularCombo"
private const val REGULAR_POPUP_VISIBLE_TAG = "spectre.regularPopupVisible"
private const val MENU_BUTTON_TAG = "spectre.menuButton"
private const val MENU_VISIBLE_TAG = "spectre.menuVisible"
private const val SPEED_SEARCH_COMBO_TAG = "spectre.speedSearchCombo"
private const val SPEED_SEARCH_POPUP_VISIBLE_TAG = "spectre.speedSearchPopupVisible"
private const val SPEED_SEARCH_VISIBLE_TAG = "spectre.speedSearchVisible"
private const val SPEED_SEARCH_QUERY_TAG = "spectre.speedSearchQuery"
