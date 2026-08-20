@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.popup

// Differential test: the custom renderer must behave exactly as Compose's own popup does, for every component
// that opens a popup. Compose's renderer is the oracle here, so a divergence is a bug in JDialogRenderer even
// when both outcomes look individually reasonable.

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.gotit.GotItButtons
import org.jetbrains.jewel.ui.component.gotit.GotItTooltip
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox
import org.junit.jupiter.api.Test

private const val OPEN_TAG = "matrix.open"
private const val POPUP_TAG = "Jewel.ComboBox.Popup"
private const val MENU_TAG = "matrix.menuPopup"
private const val SEARCH_INPUT_TAG = "SpeedSearchArea.Input"
private const val RAW_POPUP_TAG = "parity.rawPopup"

/** Set when Escape reaches the owner window's Compose tree, i.e. nothing above it consumed the key. */
private val hostSawEscape = AtomicBoolean(false)

private class Case(
    val name: String,
    val open: suspend (ComposeAutomator, RobotDriver) -> Unit,
    val measure: suspend (ComposeAutomator) -> String,
    /**
     * The absolute measurement both renderers must produce, for scenarios with no component logic masking the
     * renderer's own decision. Differential parity alone cannot catch both renderers being wrong the same way.
     */
    val expect: String? = null,
    val content: @Composable () -> Unit,
)

private fun ComposeAutomator.hasTag(tag: String) = findByTestTag(tag).isNotEmpty()

private fun ComposeAutomator.hasText(text: String) = findByText(text).isNotEmpty()

private fun host() = "hostSawEscape=" + hostSawEscape.get()

private val openByClick: suspend (ComposeAutomator, RobotDriver) -> Unit = { a, _ ->
    a.click(a.waitForNode(tag = OPEN_TAG))
    a.waitForNode(tag = POPUP_TAG)
}

/** Clicks the chevron rather than the centre, which on an EditableComboBox only focuses the text field. */
private val openByChevron: suspend (ComposeAutomator, RobotDriver) -> Unit = { a, driver ->
    val bounds = a.waitForNode(tag = OPEN_TAG).boundsOnScreen
    driver.click(bounds.x + bounds.width - 8, bounds.y + bounds.height / 2)
    a.waitForNode(tag = POPUP_TAG)
}

private val CONTROL_CASE =
    // Control: no popup at all. If this does not report hostSawEscape=true, the probe itself is broken
    // and every other hostSawEscape reading in this run is meaningless.
    Case(
        name = "control(noPopup)",
        open = { a, _ -> a.click(a.waitForNode(tag = OPEN_TAG)) },
        measure = { "hostSawEscape=" + hostSawEscape.get() },
    ) {
        DefaultButton(onClick = {}, modifier = Modifier.testTag(OPEN_TAG)) { Text("Plain button") }
    }

private val CASES =
    listOf(
        Case(name = "comboBox", open = openByClick, measure = { "popupOpen=" + it.hasTag(POPUP_TAG) + " " + host() }) {
            ComboBox(labelText = "Combo", modifier = Modifier.testTag(OPEN_TAG).width(240.dp)) { Text("Content") }
        },
        Case(
            name = "editableComboBox",
            open = openByChevron,
            measure = { "popupOpen=" + it.hasTag(POPUP_TAG) + " " + host() },
        ) {
            EditableComboBox(
                textFieldState = remember { TextFieldState("Editable") },
                modifier = Modifier.testTag(OPEN_TAG).width(240.dp),
            ) {
                Text("Content")
            }
        },
        Case(
            name = "popupMenu(focusable=true)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = MENU_TAG)
            },
            measure = { "menuOpen=" + it.hasTag(MENU_TAG) + " " + host() },
        ) {
            MenuWithSubmenu()
        },
        Case(
            name = "submenu(nested)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = MENU_TAG)
                a.click(a.waitForNode(text = "Open submenu"))
                a.waitForNode(text = "Sub item")
            },
            measure = {
                "parentMenuOpen=" + it.hasTag(MENU_TAG) + " submenuOpen=" + it.hasText("Sub item") + " " + host()
            },
        ) {
            MenuWithSubmenu()
        },
        Case(
            name = "gotIt(withButtons)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(text = "Tooltip body")
            },
            measure = { "tooltipOpen=" + it.hasText("Tooltip body") + " " + host() },
        ) {
            GotIt(buttons = GotItButtons.default())
        },
        Case(
            name = "gotIt(noButtons)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(text = "Tooltip body")
            },
            measure = { "tooltipOpen=" + it.hasText("Tooltip body") + " " + host() },
        ) {
            GotIt(buttons = GotItButtons.None)
        },
        Case(
            name = "rawPopup(dismissOnBackPress=true)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = RAW_POPUP_TAG)
            },
            measure = { "popupOpen=" + it.hasTag(RAW_POPUP_TAG) + " " + host() },
            // Escape dismisses, and the focusable popup consumes the key whether it dismissed or not.
            expect = "popupOpen=false hostSawEscape=false",
        ) {
            RawPopup(dismissOnBackPress = true)
        },
        Case(
            name = "rawPopup(nonFocusable)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = RAW_POPUP_TAG)
            },
            measure = { "popupOpen=" + it.hasTag(RAW_POPUP_TAG) + " " + host() },
            // A non-focusable popup cannot act on Escape at all: it must neither dismiss nor consume, so the
            // key reaches the host. Proves unconsumed Escape is forwarded while a native popup window exists.
            expect = "popupOpen=true hostSawEscape=true",
        ) {
            RawPopup(focusable = false)
        },
        Case(
            name = "rawPopup(dismissOnBackPress=false)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = RAW_POPUP_TAG)
            },
            measure = { "popupOpen=" + it.hasTag(RAW_POPUP_TAG) + " " + host() },
            // No dismissal, but the focusable popup still consumes the key.
            expect = "popupOpen=true hostSawEscape=false",
        ) {
            RawPopup(dismissOnBackPress = false)
        },
        Case(
            name = "speedSearchCombo(escape#1)",
            open = { a, _ ->
                a.click(a.waitForNode(tag = OPEN_TAG))
                a.waitForNode(tag = POPUP_TAG)
                a.typeText("Alpha")
                a.waitForNode(tag = SEARCH_INPUT_TAG)
            },
            measure = {
                "popupOpen=" + it.hasTag(POPUP_TAG) + " searchOpen=" + it.hasTag(SEARCH_INPUT_TAG) + " " + host()
            },
        ) {
            SpeedSearchCombo()
        },
    )

class PopupRendererParitySpectreTest {
    @Test
    fun `the custom renderer matches Compose's own popup for every component`(): Unit = runSpectreTest {
        // The control doubles as the probe's canary: if Escape does not reach the host window with no popup
        // involved, every other hostSawEscape reading is meaningless, and the parity comparison below would
        // pass vacuously on a dead probe. Gate on it explicitly.
        for (custom in listOf(false, true)) {
            assertEquals(
                expected = "hostSawEscape=true",
                actual = measure(custom = custom, case = CONTROL_CASE),
                message =
                    "Escape probe is broken on ${label(custom)}: the host window did not see Escape with no popup showing",
            )
        }

        val divergences = mutableListOf<String>()
        for (case in CASES) {
            val withCompose = measure(custom = false, case = case)
            val withJDialog = measure(custom = true, case = case)
            if (withCompose != withJDialog) {
                divergences += "${case.name}: defaultCompose[$withCompose] != JDialogRenderer[$withJDialog]"
            }
            val expected = case.expect
            if (expected != null) {
                if (withCompose != expected) {
                    divergences += "${case.name}: defaultCompose[$withCompose] != expected[$expected]"
                }
                if (withJDialog != expected) {
                    divergences += "${case.name}: JDialogRenderer[$withJDialog] != expected[$expected]"
                }
            }
        }
        assertEquals(
            emptyList(),
            divergences,
            "JDialogRenderer must be indistinguishable from Compose's own popup for these components",
        )
    }

    private suspend fun measure(custom: Boolean, case: Case): String {
        JewelFlags.useCustomPopupRenderer = custom
        val app = MatrixApp(case.content)
        app.start()
        try {
            val driver = RobotDriver.synthetic(app.awaitWindow())
            val automator = ComposeAutomator.inProcess(driver)
            case.open(automator, driver)
            automator.waitForIdle()
            hostSawEscape.set(false)

            automator.pressKey(KeyEvent.VK_ESCAPE)
            delay(1500.milliseconds)
            automator.refreshWindows()

            val result = case.measure(automator)
            println("PARITY renderer=" + label(custom) + " case=" + case.name + " " + result)
            return result
        } finally {
            app.stop()
            delay(500.milliseconds)
        }
    }

    private fun label(custom: Boolean) = if (custom) "JDialogRenderer" else "defaultCompose"
}

@Composable
private fun MenuWithSubmenu() {
    var visible by remember { mutableStateOf(false) }
    Box {
        DefaultButton(onClick = { visible = true }, modifier = Modifier.testTag(OPEN_TAG)) { Text("Menu") }
        if (visible) {
            PopupMenu(
                onDismissRequest = {
                    visible = false
                    true
                },
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.testTag(MENU_TAG),
            ) {
                submenu(submenu = { selectableItem(false, onClick = {}) { Text("Sub item") } }) { Text("Open submenu") }
            }
        }
    }
}

@Composable
private fun GotIt(buttons: GotItButtons) {
    var visible by remember { mutableStateOf(false) }
    GotItTooltip(text = "Tooltip body", visible = visible, onDismiss = { visible = false }, buttons = buttons) {
        DefaultButton(onClick = { visible = true }, modifier = Modifier.testTag(OPEN_TAG)) { Text("Show") }
    }
}

/**
 * A popup with no component logic behind it: nothing else handles Escape, so what happens is entirely the renderer's
 * decision. Every other case in this matrix has a component that closes itself on Escape, which masks whatever the
 * renderer does.
 */
@Composable
private fun RawPopup(focusable: Boolean = true, dismissOnBackPress: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    Box {
        DefaultButton(onClick = { visible = true }, modifier = Modifier.testTag(OPEN_TAG)) { Text("Open raw popup") }
        if (visible) {
            PopupContainer(
                onDismissRequest = { visible = false },
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.testTag(RAW_POPUP_TAG),
                popupProperties = PopupProperties(focusable = focusable, dismissOnBackPress = dismissOnBackPress),
            ) {
                Text("Raw popup content")
            }
        }
    }
}

@Composable
private fun SpeedSearchCombo() {
    val state = rememberSpeedSearchState()
    SpeedSearchArea(state = state, dismissOnLoseFocus = false) {
        SpeedSearchableComboBox(
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndex = 0,
            onSelectedItemChange = {},
            modifier = Modifier.testTag(OPEN_TAG).width(240.dp),
        )
    }
}

private class MatrixApp(private val content: @Composable () -> Unit) {
    private val exitApplication = AtomicReference<(() -> Unit)?>(null)
    private val window = AtomicReference<ComposeWindow?>(null)

    fun start() {
        thread(name = "spectre-matrix-window", isDaemon = true) {
            application(exitProcessOnExit = false) {
                exitApplication.set(::exitApplication)
                JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel popup matrix") {
                    this@MatrixApp.window.compareAndSet(null, window)
                    IntUiTheme {
                        Column(
                            modifier =
                                Modifier.padding(24.dp).onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                        hostSawEscape.set(true)
                                    }
                                    false
                                }
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        exitApplication.get()?.invoke()
        window.set(null)
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
