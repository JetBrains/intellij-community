@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
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
import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox
import org.junit.jupiter.api.Test

// Headful, and deliberately not a jps_test: the app under test must keep a standalone-only runtime closure,
// with no IntelliJ Platform classes on the classpath. Runs in CI on any agent with a display:
//   bazel test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-spectre-tests
//
// Every assertion here is on what is actually on screen. The renderers under test put popups in their own native
// windows, so a popup can be gone while Compose still believes it is showing, or the reverse: asserting on the
// components' own visibility state alone would pass straight through the bug class these tests exist to catch.
// The state is still checked, but only ever as a cross-check that it agrees with the screen.
class CustomPopupRendererSpectreTest {
    @Test
    fun `escape closes a hovered combo box`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val app = SpectreTestApplication()
        app.start()
        try {
            val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

            automator.click(automator.waitForNode(tag = REGULAR_COMBO_TAG))
            automator.waitForNode(tag = COMBO_BOX_POPUP_TAG)

            // The pointer remains inside the ComboBox after click, reproducing the hover path that
            // previously let the popup's JDialogRenderer consume Escape as a no-op dismissal.
            automator.pressKey(KeyEvent.VK_ESCAPE)
            automator.waitUntilGone("The ComboBox popup") { findByTestTag(COMBO_BOX_POPUP_TAG) }
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
                automator.waitForNode(tag = MENU_POPUP_TAG)

                // Spectre routes synthetic key events to the window under its injected pointer. Move that pointer back
                // to the owner window to cover focusable popups that have not taken native focus yet.
                automator.moveTo(menuButton)
                automator.waitForIdle()

                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone("The menu popup") { findByTestTag(MENU_POPUP_TAG) }
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
                automator.waitForNode(tag = COMBO_BOX_POPUP_TAG)

                automator.typeText("Alpha")
                val searchInput = automator.waitForNode(tag = SPEED_SEARCH_INPUT_TAG)
                assertEquals("Alpha", searchInput.editableText, "The speed search field should show what was typed")

                // The first Escape belongs to speed search, and must not reach the popup behind it.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone("The speed search field") { findByTestTag(SPEED_SEARCH_INPUT_TAG) }
                assertTrue(
                    automator.isPresent { findByTestTag(COMBO_BOX_POPUP_TAG) },
                    "The first Escape dismissed speed search, so the popup behind it must still be open",
                )

                // Only once speed search is gone does Escape reach the popup.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone("The speed searchable ComboBox popup") { findByTestTag(COMBO_BOX_POPUP_TAG) }
            } finally {
                app.stop()
            }
        }

    @Test
    fun `clicking the chevron of an open combo box closes it without reopening it`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication()
            app.start()
            try {
                val driver = RobotDriver.synthetic(app.awaitWindow())
                val automator = ComposeAutomator.inProcess(driver)

                val comboBox = automator.waitForNode(tag = REGULAR_COMBO_TAG)
                automator.click(comboBox)
                automator.waitForNode(tag = COMBO_BOX_POPUP_TAG)

                // Suppressing dismissal while the ComboBox is hovered is what stops the click-outside dismissal
                // from closing the popup before the chevron's own handler runs and immediately reopens it.
                val bounds = comboBox.boundsOnScreen
                driver.click(bounds.x + bounds.width - CHEVRON_INSET, bounds.y + bounds.height / 2)

                automator.waitUntilGone("The ComboBox popup") { findByTestTag(COMBO_BOX_POPUP_TAG) }
                automator.waitForIdle()
                assertFalse(
                    automator.isPresent { findByTestTag(COMBO_BOX_POPUP_TAG) },
                    "The chevron click must not reopen the popup it just closed",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `escape closes a hovered editable combo box`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val app = SpectreTestApplication()
        app.start()
        try {
            val driver = RobotDriver.synthetic(app.awaitWindow())
            val automator = ComposeAutomator.inProcess(driver)

            // Only the chevron opens the popup here; clicking the text field just focuses it.
            val comboBox = automator.waitForNode(tag = EDITABLE_COMBO_TAG)
            val bounds = comboBox.boundsOnScreen
            driver.click(bounds.x + bounds.width - CHEVRON_INSET, bounds.y + bounds.height / 2)
            automator.waitForNode(tag = COMBO_BOX_POPUP_TAG)

            // The pointer is left on the chevron, which is the hover path that previously had the renderer
            // consume Escape as a no-op dismissal.
            automator.pressKey(KeyEvent.VK_ESCAPE)
            automator.waitUntilGone("The EditableComboBox popup") { findByTestTag(COMBO_BOX_POPUP_TAG) }
        } finally {
            app.stop()
        }
    }
}

/**
 * Waits until [find] matches nothing, across every window Spectre tracks.
 *
 * Spectre only ships a wait-for-presence helper, but a popup lives in its own native window, and these tests need to
 * watch that window go away.
 */
private suspend fun ComposeAutomator.waitUntilGone(
    description: String,
    find: ComposeAutomator.() -> List<AutomatorNode>,
) {
    repeat(POLL_ATTEMPTS) {
        if (!isPresent(find)) return
        delay(POLL_INTERVAL_MS.milliseconds)
    }
    error("$description was still on screen after ${POLL_ATTEMPTS * POLL_INTERVAL_MS} ms")
}

private fun ComposeAutomator.isPresent(find: ComposeAutomator.() -> List<AutomatorNode>): Boolean {
    refreshWindows()
    return find().isNotEmpty()
}

private fun runSpectreTestWithCustomPopupRenderer(block: suspend CoroutineScope.() -> Unit): Unit = runSpectreTest {
    assertTrue(JewelFlags.useCustomPopupRenderer, "spectreTest must enable JDialogRenderer")
    block()
}

@Composable
private fun PopupEscapeScreen() {
    val editableTextFieldState = remember { TextFieldState("Editable") }
    var menuVisible by remember { mutableStateOf(false) }
    val speedSearchState = rememberSpeedSearchState()

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ComboBox(labelText = "Regular ComboBox", modifier = Modifier.testTag(REGULAR_COMBO_TAG).width(240.dp)) {
            Text("Regular popup content")
        }

        EditableComboBox(
            textFieldState = editableTextFieldState,
            modifier = Modifier.testTag(EDITABLE_COMBO_TAG).width(240.dp),
        ) {
            Text("Editable popup content")
        }

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
                    modifier = Modifier.testTag(MENU_POPUP_TAG),
                ) {
                    selectableItem(selected = false, onClick = {}) { Text("Menu item") }
                }
            }
        }

        SpeedSearchArea(state = speedSearchState, dismissOnLoseFocus = false) {
            SpeedSearchableComboBox(
                items = listOf("Alpha", "Beta", "Gamma"),
                selectedIndex = 0,
                onSelectedItemChange = {},
                modifier = Modifier.testTag(SPEED_SEARCH_COMBO_TAG).width(240.dp),
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
            delay(100.milliseconds)
        }
        error("The Compose test window was not created")
    }
}

/** Set by both ComboBox and EditableComboBox on their popup content, including the speed searchable variant. */
private const val COMBO_BOX_POPUP_TAG = "Jewel.ComboBox.Popup"

/** Set by SpeedSearchArea on its search field. */
private const val SPEED_SEARCH_INPUT_TAG = "SpeedSearchArea.Input"

private const val REGULAR_COMBO_TAG = "spectre.regularCombo"
private const val EDITABLE_COMBO_TAG = "spectre.editableCombo"
private const val MENU_BUTTON_TAG = "spectre.menuButton"
private const val MENU_POPUP_TAG = "spectre.menuPopup"
private const val SPEED_SEARCH_COMBO_TAG = "spectre.speedSearchCombo"

/** Distance from the ComboBox's trailing edge that reliably lands on the chevron rather than the label. */
private const val CHEVRON_INSET = 8

private const val POLL_ATTEMPTS = 100
private const val POLL_INTERVAL_MS = 100L
