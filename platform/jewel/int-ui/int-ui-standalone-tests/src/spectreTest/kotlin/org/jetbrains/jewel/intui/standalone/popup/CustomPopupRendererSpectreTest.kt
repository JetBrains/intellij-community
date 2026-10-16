@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.application
import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JDialog
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.awaitWindow
import org.jetbrains.jewel.intui.standalone.stopApplicationWindow
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

// Headful, and deliberately not a jps_test: the app under test must keep a standalone-only runtime closure,
// with no IntelliJ Platform classes on the classpath. Runs in CI on any agent with a display:
//   bazel test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-e2e-tests
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
            automator.waitUntilGone(tag = COMBO_BOX_POPUP_TAG, timeout = 10.seconds)
        } finally {
            app.stop()
        }
    }

    @Disabled(
        "JDialogRenderer puts the popup in its own native window, and Compose inside it never receives a pointer " +
            "exit when the cursor leaves that window, so onHover(false) never fires and hover-driven state stays " +
            "stuck. Needs a renderer-level signal. Enable once that exists."
    )
    @Test
    fun `moving the pointer off a menu popup disarms its item`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val app = SpectreTestApplication()
        app.start()
        try {
            val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

            val menuButton = automator.waitForNode(tag = MENU_BUTTON_TAG)
            automator.click(menuButton)
            automator.waitForNode(tag = MENU_POPUP_TAG)

            automator.moveTo(automator.waitForNode(text = MENU_ITEM_TEXT))
            automator.waitForIdle()
            assertTrue(automator.waitForNode(text = MENU_ITEM_TEXT).isFocused, "the hovered item should be armed")

            // Back onto the owner window, which leaves the popup's own native window entirely. The renderer has to
            // surface that as a pointer exit, or the item stays armed with the pointer nowhere near it.
            automator.moveTo(menuButton)
            automator.waitForIdle()
            assertFalse(
                automator.waitForNode(text = MENU_ITEM_TEXT).isFocused,
                "the item should disarm once the pointer leaves the popup",
            )
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
                automator.waitUntilGone(tag = MENU_POPUP_TAG, timeout = 10.seconds)
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
                automator.waitUntilGone(tag = SPEED_SEARCH_INPUT_TAG, timeout = 10.seconds)
                assertTrue(
                    automator.isPresent { findByTestTag(COMBO_BOX_POPUP_TAG) },
                    "The first Escape dismissed speed search, so the popup behind it must still be open",
                )

                // Only once speed search is gone does Escape reach the popup.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone(tag = COMBO_BOX_POPUP_TAG, timeout = 10.seconds)
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

                automator.waitUntilGone(tag = COMBO_BOX_POPUP_TAG, timeout = 10.seconds)
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
            automator.waitUntilGone(tag = COMBO_BOX_POPUP_TAG, timeout = 10.seconds)
        } finally {
            app.stop()
        }
    }

    @DisabledOnOs(
        OS.LINUX,
        disabledReason =
            "JDialogRenderer falls back to ComposePopup on Linux when rounded corners and interop blending are unavailable.",
    )
    @Test
    fun `popup window is never shown at screen origin or 1x1 size`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val app = SpectreTestApplication()
        app.start()
        val observedVisibleBounds = CopyOnWriteArrayList<Rectangle>()
        var listener: AWTEventListener? = null
        try {
            val hostWindow = app.awaitWindow()
            SwingUtilities.invokeAndWait { hostWindow.setLocation(300, 200) }
            val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(hostWindow))
            automator.waitForIdle()

            listener = AWTEventListener { event ->
                val sourceComponent = event.source as? Component ?: return@AWTEventListener
                val dialog =
                    sourceComponent as? JDialog ?: SwingUtilities.getWindowAncestor(sourceComponent) as? JDialog
                if (dialog != null && dialog.owner == hostWindow && dialog.isShowing) {
                    observedVisibleBounds.add(dialog.bounds)
                }
            }
            Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                    listener,
                    AWTEvent.HIERARCHY_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK,
                )

            val menuButton = automator.waitForNode(tag = MENU_BUTTON_TAG)
            automator.click(menuButton)
            automator.waitForNode(tag = MENU_POPUP_TAG)
            automator.waitForIdle()

            assertTrue(observedVisibleBounds.isNotEmpty(), "Expected to observe visible popup dialog bounds")
            assertTrue(
                observedVisibleBounds.none { it.x < hostWindow.x || it.y < hostWindow.y },
                "Popup window was shown at top-left of screen before repositioning: $observedVisibleBounds",
            )
            assertTrue(
                observedVisibleBounds.none { it.width <= 1 || it.height <= 1 },
                "Popup window was shown with placeholder size: $observedVisibleBounds",
            )
        } finally {
            if (listener != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            }
            app.stop()
        }
    }

    @DisabledOnOs(
        OS.LINUX,
        disabledReason =
            "JDialogRenderer falls back to ComposePopup on Linux when rounded corners and interop blending are unavailable.",
    )
    @Test
    fun `popup window applies window shape before becoming visible`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val firstShapeCallDialogVisible = AtomicReference<Boolean?>(null)
        val firstShapeCallSize = AtomicReference<Dimension?>(null)
        val hostWindowRef = AtomicReference<ComposeWindow?>(null)
        val disposableEffectRuns = AtomicInteger(0)
        val disposableEffectDisposals = AtomicInteger(0)
        val launchedEffectRuns = AtomicInteger(0)
        var shapedPopupVisible by mutableStateOf(false)

        val app = SpectreTestApplication {
            Box(modifier = Modifier.padding(24.dp)) {
                DefaultButton(
                    onClick = { shapedPopupVisible = true },
                    modifier = Modifier.testTag(SHAPED_POPUP_BUTTON_TAG),
                ) {
                    Text("Show shaped popup")
                }

                if (shapedPopupVisible) {
                    Popup(
                        popupPositionProvider = AnchorBottomLeftPopupPositionProvider,
                        cornerSize = CornerSize(8.dp),
                        onDismissRequest = { _ -> shapedPopupVisible = false },
                        windowShape = { logicalSize ->
                            val currentHostWindow = hostWindowRef.get()
                            val isDialogVisible =
                                currentHostWindow?.ownedWindows?.filterIsInstance<JDialog>()?.any { it.isVisible }
                                    ?: false
                            firstShapeCallDialogVisible.compareAndSet(null, isDialogVisible)
                            firstShapeCallSize.compareAndSet(null, Dimension(logicalSize.width, logicalSize.height))
                            Rectangle(0, 0, logicalSize.width, logicalSize.height)
                        },
                    ) {
                        DisposableEffect(Unit) {
                            disposableEffectRuns.incrementAndGet()
                            onDispose { disposableEffectDisposals.incrementAndGet() }
                        }
                        LaunchedEffect(Unit) { launchedEffectRuns.incrementAndGet() }

                        Box(modifier = Modifier.size(200.dp, 120.dp).testTag(SHAPED_POPUP_TAG)) {
                            Text("Shaped popup content")
                        }
                    }
                }
            }
        }
        app.start()

        val observedShowingShapes = CopyOnWriteArrayList<Pair<Rectangle, Rectangle?>>()
        var listener: AWTEventListener? = null
        try {
            val hostWindow = app.awaitWindow()
            hostWindowRef.set(hostWindow)
            val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(hostWindow))
            automator.waitForIdle()

            listener = AWTEventListener { event ->
                val sourceComponent = event.source as? Component ?: return@AWTEventListener
                val dialog =
                    sourceComponent as? JDialog ?: SwingUtilities.getWindowAncestor(sourceComponent) as? JDialog
                if (dialog != null && dialog.owner == hostWindow && dialog.isShowing) {
                    observedShowingShapes.add(dialog.bounds to dialog.shape?.bounds)
                }
            }
            Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                    listener,
                    AWTEvent.HIERARCHY_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK,
                )

            automator.click(automator.waitForNode(tag = SHAPED_POPUP_BUTTON_TAG))
            automator.waitForNode(tag = SHAPED_POPUP_TAG)
            automator.waitForIdle()

            assertEquals(
                false,
                firstShapeCallDialogVisible.get(),
                "windowShape must be applied before the popup JDialog becomes visible",
            )
            val measuredShapeSize = assertNotNull(firstShapeCallSize.get(), "Expected windowShape to be invoked")
            assertEquals(Dimension(200, 120), measuredShapeSize)
            assertEquals(1, disposableEffectRuns.get(), "DisposableEffect in popup content must run only once")
            assertEquals(1, launchedEffectRuns.get(), "LaunchedEffect in popup content must run only once")
            assertEquals(0, disposableEffectDisposals.get(), "DisposableEffect must not dispose while popup is open")

            assertTrue(observedShowingShapes.isNotEmpty(), "Expected to observe showing popup dialog events")
            assertTrue(
                observedShowingShapes.all { (dialogBounds, shapeBounds) ->
                    shapeBounds == Rectangle(0, 0, dialogBounds.width, dialogBounds.height)
                },
                "Popup JDialog was shown before its windowShape matched its bounds: $observedShowingShapes",
            )

            shapedPopupVisible = false
            automator.waitUntilGone(tag = SHAPED_POPUP_TAG, timeout = 10.seconds)
            assertEquals(1, disposableEffectDisposals.get(), "DisposableEffect must dispose once when popup closes")
        } finally {
            if (listener != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            }
            app.stop()
        }
    }
}

private object AnchorBottomLeftPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom)
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
                    selectableItem(selected = false, onClick = {}) { Text(MENU_ITEM_TEXT) }
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
    private val appThread = AtomicReference<Thread?>(null)

    fun start() {
        val startedThread =
            thread(name = "spectre-popup-test-window", isDaemon = true) {
                application(exitProcessOnExit = false) {
                    exitApplication.set(::exitApplication)
                    JewelWindow(onCloseRequest = ::exitApplication, title = "Jewel Spectre popup test") {
                        this@SpectreTestApplication.window.compareAndSet(null, window)
                        IntUiTheme { content() }
                    }
                }
            }
        appThread.set(startedThread)
    }

    fun stop() {
        stopApplicationWindow(exitApplication, window, appThread)
    }

    suspend fun awaitWindow(): ComposeWindow = window.awaitWindow()
}

/** Set by both ComboBox and EditableComboBox on their popup content, including the speed searchable variant. */
private const val COMBO_BOX_POPUP_TAG = "Jewel.ComboBox.Popup"

/** Set by SpeedSearchArea on its search field. */
private const val SPEED_SEARCH_INPUT_TAG = "SpeedSearchArea.Input"

private const val REGULAR_COMBO_TAG = "spectre.regularCombo"
private const val EDITABLE_COMBO_TAG = "spectre.editableCombo"
private const val MENU_BUTTON_TAG = "spectre.menuButton"
private const val MENU_POPUP_TAG = "spectre.menuPopup"
private const val MENU_ITEM_TEXT = "Menu item"
private const val SPEED_SEARCH_COMBO_TAG = "spectre.speedSearchCombo"
private const val SHAPED_POPUP_BUTTON_TAG = "spectre.shapedPopupButton"
private const val SHAPED_POPUP_TAG = "spectre.shapedPopup"

/** Distance from the ComboBox's trailing edge that reliably lands on the chevron rather than the label. */
private const val CHEVRON_INSET = 8
