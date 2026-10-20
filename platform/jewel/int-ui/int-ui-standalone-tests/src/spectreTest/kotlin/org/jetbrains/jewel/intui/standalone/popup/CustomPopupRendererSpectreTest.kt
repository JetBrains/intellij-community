@file:OptIn(ExperimentalJewelApi::class)

package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.intui.standalone.awaitWindow
import org.jetbrains.jewel.intui.standalone.stopApplicationWindow
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.window.Window as JewelWindow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.MenuComboBox
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
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
            "The non-blending Window.setShape fallback requires JBR.isRoundedCornersManagerSupported(), which is unavailable on Linux.",
    )
    @Test
    fun `popup window applies window shape before becoming visible`(): Unit = runSpectreTestWithCustomPopupRenderer {
        val previousBlending = System.getProperty("compose.interop.blending")
        System.setProperty("compose.interop.blending", "false")
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
                            val isPopupWindowVisible = currentHostWindow?.ownedWindows?.any { it.isVisible } ?: false
                            firstShapeCallDialogVisible.compareAndSet(null, isPopupWindowVisible)
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
                val popupWindow = sourceComponent as? Window ?: SwingUtilities.getWindowAncestor(sourceComponent)
                if (popupWindow != null && popupWindow.owner == hostWindow && popupWindow.isShowing) {
                    observedShowingShapes.add(popupWindow.bounds to popupWindow.shape?.bounds)
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
                "windowShape must be applied before the popup window becomes visible",
            )
            val measuredShapeSize = assertNotNull(firstShapeCallSize.get(), "Expected windowShape to be invoked")
            assertEquals(Dimension(200, 120), measuredShapeSize)
            assertEquals(1, disposableEffectRuns.get(), "DisposableEffect in popup content must run only once")
            assertEquals(1, launchedEffectRuns.get(), "LaunchedEffect in popup content must run only once")
            assertEquals(0, disposableEffectDisposals.get(), "DisposableEffect must not dispose while popup is open")

            assertTrue(observedShowingShapes.isNotEmpty(), "Expected to observe showing popup window events")
            assertTrue(
                observedShowingShapes.all { (windowBounds, shapeBounds) ->
                    shapeBounds == Rectangle(0, 0, windowBounds.width, windowBounds.height)
                },
                "Popup window was shown before its windowShape matched its bounds: $observedShowingShapes",
            )

            shapedPopupVisible = false
            automator.waitUntilGone(tag = SHAPED_POPUP_TAG, timeout = 10.seconds)
            assertEquals(1, disposableEffectDisposals.get(), "DisposableEffect must dispose once when popup closes")
        } finally {
            if (listener != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            }
            if (previousBlending == null) {
                System.clearProperty("compose.interop.blending")
            } else {
                System.setProperty("compose.interop.blending", previousBlending)
            }
            app.stop()
        }
    }

    @Test
    fun `non-focusable popup uses JWindow and does not dismiss an open focusable popup`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication {
                var focusableVisible by remember { mutableStateOf(false) }
                var nonFocusableVisible by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = { focusableVisible = true },
                        modifier = Modifier.testTag("spectre.openFocusable"),
                    ) {
                        Text("Open focusable")
                    }

                    DefaultButton(
                        onClick = { nonFocusableVisible = true },
                        modifier = Modifier.testTag("spectre.openNonFocusable"),
                    ) {
                        Text("Open non-focusable")
                    }

                    if (focusableVisible) {
                        PopupContainer(
                            onDismissRequest = { focusableVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true),
                            modifier = Modifier.testTag("spectre.focusablePopup"),
                        ) {
                            DefaultButton(
                                onClick = { nonFocusableVisible = true },
                                modifier = Modifier.testTag("spectre.openTooltipFromPopup"),
                            ) {
                                Text("Show tooltip popup")
                            }
                        }
                    }

                    if (nonFocusableVisible) {
                        PopupContainer(
                            onDismissRequest = { nonFocusableVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = false),
                            modifier = Modifier.testTag("spectre.nonFocusablePopup"),
                        ) {
                            Text("Non-focusable content")
                        }
                    }
                }
            }
            app.start()
            try {
                val hostWindow = app.awaitWindow()
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(hostWindow))

                automator.click(automator.waitForNode(tag = "spectre.openFocusable"))
                automator.waitForNode(tag = "spectre.focusablePopup")

                automator.click(automator.waitForNode(tag = "spectre.openTooltipFromPopup"))
                automator.waitForNode(tag = "spectre.nonFocusablePopup")
                automator.waitForIdle()

                assertTrue(
                    automator.isPresent { findByTestTag("spectre.focusablePopup") },
                    "Showing a non-focusable popup must not dismiss an open focusable popup",
                )
                assertTrue(
                    hostWindow.ownedWindows.any { it is JDialog && it.isShowing },
                    "Focusable popup should use a JDialog",
                )
                assertTrue(
                    hostWindow.ownedWindows.any { it is JWindow && it.isShowing },
                    "Non-focusable popup should use a JWindow",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `focusable popup opened on mouse press stays open on the opening mouse release`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier =
                            Modifier.size(120.dp, 40.dp).testTag("spectre.pressTrigger").pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press) {
                                            popupVisible = true
                                        }
                                    }
                                }
                            }
                    ) {
                        Text("Press to open")
                    }

                    if (popupVisible) {
                        PopupContainer(
                            onDismissRequest = { popupVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true),
                            modifier = Modifier.testTag("spectre.pressOpenedPopup"),
                        ) {
                            Text("Opened on press")
                        }
                    }
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                automator.click(automator.waitForNode(tag = "spectre.pressTrigger"))
                automator.waitForNode(tag = "spectre.pressOpenedPopup")
                automator.waitForIdle()

                assertTrue(
                    automator.isPresent { findByTestTag("spectre.pressOpenedPopup") },
                    "The mouse release that completes the opening press must not dismiss the popup",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `focusable popup routes owner window key events to popup content and ignores modified escape`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val ownerSawKeyDown = AtomicBoolean(false)
            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }
                val textFieldState = remember { TextFieldState("") }
                val popupInputFocusRequester = remember { FocusRequester() }
                val ownerFocusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) { ownerFocusRequester.requestFocus() }

                Column(
                    modifier =
                        Modifier.padding(24.dp).focusRequester(ownerFocusRequester).focusable().onPreviewKeyEvent {
                            event ->
                            if (
                                popupVisible &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.key != Key.ShiftLeft &&
                                    event.key != Key.ShiftRight
                            ) {
                                ownerSawKeyDown.set(true)
                            }
                            false
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DefaultButton(
                        onClick = { popupVisible = true },
                        modifier = Modifier.testTag("spectre.openKeyRoutingPopup"),
                    ) {
                        Text("Open popup")
                    }

                    if (popupVisible) {
                        PopupContainer(
                            onDismissRequest = { popupVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true),
                            modifier = Modifier.testTag("spectre.keyRoutingPopup"),
                        ) {
                            LaunchedEffect(Unit) { popupInputFocusRequester.requestFocus() }
                            TextField(
                                state = textFieldState,
                                modifier =
                                    Modifier.width(180.dp)
                                        .focusRequester(popupInputFocusRequester)
                                        .testTag("spectre.popupTextField"),
                            )
                        }
                    }
                }
            }
            app.start()
            try {
                val hostWindow = app.awaitWindow()
                val driver = RobotDriver.synthetic(hostWindow)
                val automator = ComposeAutomator.inProcess(driver)

                val openButton = automator.waitForNode(tag = "spectre.openKeyRoutingPopup")
                automator.click(openButton)
                automator.waitForNode(tag = "spectre.keyRoutingPopup")

                // Move the pointer onto the owner window so synthetic key events target the owner window,
                // matching the runtime state where the focus-stealing workaround keeps the owner window active.
                automator.moveTo(openButton)
                automator.waitForIdle()
                ownerSawKeyDown.set(false)

                automator.typeText("abc")
                automator.waitForIdle()

                val popupInput = automator.waitForNode(tag = "spectre.popupTextField")
                assertEquals("abc", popupInput.editableText, "Popup text field should receive keys from owner window")
                assertFalse(ownerSawKeyDown.get(), "Owner window must not receive keys consumed by the focusable popup")

                // Shift+Escape must be delivered to the popup rather than treated as an unconditional dismiss request.
                SwingUtilities.invokeAndWait {
                    val now = System.currentTimeMillis()
                    hostWindow.dispatchEvent(
                        KeyEvent(
                            hostWindow,
                            KeyEvent.KEY_PRESSED,
                            now,
                            KeyEvent.SHIFT_DOWN_MASK,
                            KeyEvent.VK_ESCAPE,
                            KeyEvent.CHAR_UNDEFINED,
                        )
                    )
                    hostWindow.dispatchEvent(
                        KeyEvent(
                            hostWindow,
                            KeyEvent.KEY_RELEASED,
                            now,
                            KeyEvent.SHIFT_DOWN_MASK,
                            KeyEvent.VK_ESCAPE,
                            KeyEvent.CHAR_UNDEFINED,
                        )
                    )
                }
                automator.waitForIdle()
                assertTrue(
                    automator.isPresent { findByTestTag("spectre.keyRoutingPopup") },
                    "Shift+Escape must not dismiss the popup",
                )

                // Unmodified Escape dismisses the popup.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone(tag = "spectre.keyRoutingPopup", timeout = 10.seconds)
            } finally {
                app.stop()
            }
        }

    @Test
    fun `non-focusable popup with text field leaves typed keys to owner window and keeps popup field empty`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val ownerFieldState = TextFieldState("")
            val popupFieldState = TextFieldState("")
            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }
                val ownerInputFocusRequester = remember { FocusRequester() }
                val popupInputFocusRequester = remember { FocusRequester() }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = {
                            popupVisible = true
                            ownerInputFocusRequester.requestFocus()
                        },
                        modifier = Modifier.testTag("spectre.openNonFocusableInputPopup"),
                    ) {
                        Text("Open non-focusable popup")
                    }

                    TextField(
                        state = ownerFieldState,
                        modifier =
                            Modifier.width(180.dp)
                                .focusRequester(ownerInputFocusRequester)
                                .testTag("spectre.ownerInputTextField"),
                    )

                    if (popupVisible) {
                        PopupContainer(
                            onDismissRequest = { popupVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = false, dismissOnClickOutside = false),
                            modifier = Modifier.testTag("spectre.nonFocusableInputPopup"),
                        ) {
                            LaunchedEffect(Unit) { popupInputFocusRequester.requestFocus() }
                            TextField(
                                state = popupFieldState,
                                modifier =
                                    Modifier.width(180.dp)
                                        .focusRequester(popupInputFocusRequester)
                                        .testTag("spectre.nonFocusablePopupTextField"),
                            )
                        }
                    }
                }
            }
            app.start()
            try {
                val hostWindow = app.awaitWindow()
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(hostWindow))

                automator.click(automator.waitForNode(tag = "spectre.openNonFocusableInputPopup"))
                automator.waitForNode(tag = "spectre.nonFocusableInputPopup")
                automator.waitForNode(tag = "spectre.nonFocusablePopupTextField")
                automator.waitForIdle()

                automator.typeText("abc")
                automator.waitForIdle()

                val ownerInput = automator.waitForNode(tag = "spectre.ownerInputTextField")
                val popupInput = automator.waitForNode(tag = "spectre.nonFocusablePopupTextField")
                assertEquals("abc", ownerInput.editableText, "Owner text field must receive the typed keys")
                assertEquals("abc", ownerFieldState.text.toString(), "Owner text field state must contain typed text")
                assertEquals("", popupInput.editableText, "Non-focusable popup text field must stay empty")
                assertEquals(
                    "",
                    popupFieldState.text.toString(),
                    "Non-focusable popup text field state must stay empty",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `inner popup content can consume escape before focusable popup dismisses`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }
                val textFieldState = remember { TextFieldState("") }
                val popupInputFocusRequester = remember { FocusRequester() }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = { popupVisible = true },
                        modifier = Modifier.testTag("spectre.openFilterPopup"),
                    ) {
                        Text("Open filter popup")
                    }

                    if (popupVisible) {
                        PopupContainer(
                            onDismissRequest = { popupVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true),
                            modifier = Modifier.testTag("spectre.filterPopup"),
                        ) {
                            LaunchedEffect(Unit) { popupInputFocusRequester.requestFocus() }
                            TextField(
                                state = textFieldState,
                                modifier =
                                    Modifier.width(180.dp)
                                        .focusRequester(popupInputFocusRequester)
                                        .onPreviewKeyEvent { event ->
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                    event.key == Key.Escape &&
                                                    textFieldState.text.isNotEmpty()
                                            ) {
                                                textFieldState.clearText()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag("spectre.filterTextField"),
                            )
                        }
                    }
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                automator.click(automator.waitForNode(tag = "spectre.openFilterPopup"))
                automator.waitForNode(tag = "spectre.filterPopup")

                val filterInput = automator.waitForNode(tag = "spectre.filterTextField")
                automator.click(filterInput)
                automator.typeText("query")
                automator.waitForIdle()
                assertEquals("query", automator.waitForNode(tag = "spectre.filterTextField").editableText)

                // First Escape clears the inner filter input and keeps the popup open.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitForIdle()
                assertTrue(
                    automator.isPresent { findByTestTag("spectre.filterPopup") },
                    "First Escape was consumed by the inner filter input and must not dismiss the popup",
                )
                assertEquals("", automator.waitForNode(tag = "spectre.filterTextField").editableText)

                // Second Escape is not consumed by the empty filter input and dismisses the popup.
                automator.pressKey(KeyEvent.VK_ESCAPE)
                automator.waitUntilGone(tag = "spectre.filterPopup", timeout = 10.seconds)
            } finally {
                app.stop()
            }
        }

    @Test
    fun `clicking a toggle button in the owner window closes an open focusable popup without reopening it`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = { popupVisible = !popupVisible },
                        modifier = Modifier.testTag("spectre.toggleFocusablePopup"),
                    ) {
                        Text("Toggle focusable popup")
                    }

                    if (popupVisible) {
                        PopupContainer(
                            onDismissRequest = { popupVisible = false },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true, dismissOnClickOutside = true),
                            modifier = Modifier.testTag("spectre.toggledFocusablePopup"),
                        ) {
                            Text("Focusable popup content")
                        }
                    }
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                val toggleButton = automator.waitForNode(tag = "spectre.toggleFocusablePopup")
                automator.click(toggleButton)
                automator.waitForNode(tag = "spectre.toggledFocusablePopup")

                // Click the owner window toggle button while the focusable popup is open.
                automator.click(toggleButton)
                automator.waitUntilGone(tag = "spectre.toggledFocusablePopup", timeout = 10.seconds)
                automator.waitForIdle()
                assertFalse(
                    automator.isPresent { findByTestTag("spectre.toggledFocusablePopup") },
                    "Clicking the toggle button must close the focusable popup without reopening it",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `clicking inside nested popups keeps ancestor popups open until owner toggle click`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication {
                var parentVisible by remember { mutableStateOf(false) }
                var childVisible by remember { mutableStateOf(false) }
                var grandchildVisible by remember { mutableStateOf(false) }
                var grandchildClickCount by remember { mutableIntStateOf(0) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = { parentVisible = !parentVisible },
                        modifier = Modifier.testTag("spectre.openParentPopup"),
                    ) {
                        Text("Open parent popup")
                    }

                    if (parentVisible) {
                        PopupContainer(
                            onDismissRequest = {
                                grandchildVisible = false
                                childVisible = false
                                parentVisible = false
                            },
                            horizontalAlignment = Alignment.Start,
                            popupProperties = PopupProperties(focusable = true, dismissOnClickOutside = true),
                            modifier = Modifier.testTag("spectre.parentPopup"),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                DefaultButton(
                                    onClick = { childVisible = true },
                                    modifier = Modifier.testTag("spectre.openChildPopup"),
                                ) {
                                    Text("Open child popup")
                                }

                                if (childVisible) {
                                    PopupContainer(
                                        onDismissRequest = {
                                            grandchildVisible = false
                                            childVisible = false
                                        },
                                        horizontalAlignment = Alignment.Start,
                                        popupProperties =
                                            PopupProperties(focusable = true, dismissOnClickOutside = true),
                                        modifier = Modifier.testTag("spectre.childPopup"),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            DefaultButton(
                                                onClick = { grandchildVisible = true },
                                                modifier = Modifier.testTag("spectre.openGrandchildPopup"),
                                            ) {
                                                Text("Open grandchild popup")
                                            }

                                            if (grandchildVisible) {
                                                PopupContainer(
                                                    onDismissRequest = { grandchildVisible = false },
                                                    horizontalAlignment = Alignment.Start,
                                                    popupProperties =
                                                        PopupProperties(focusable = true, dismissOnClickOutside = true),
                                                    modifier = Modifier.testTag("spectre.grandchildPopup"),
                                                ) {
                                                    DefaultButton(
                                                        onClick = { grandchildClickCount++ },
                                                        modifier = Modifier.testTag("spectre.grandchildActionButton"),
                                                    ) {
                                                        Text("Grandchild clicks: $grandchildClickCount")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                val openParentButton = automator.waitForNode(tag = "spectre.openParentPopup")
                automator.click(openParentButton)
                automator.waitForNode(tag = "spectre.parentPopup")

                automator.click(automator.waitForNode(tag = "spectre.openChildPopup"))
                automator.waitForNode(tag = "spectre.childPopup")

                automator.click(automator.waitForNode(tag = "spectre.openGrandchildPopup"))
                automator.waitForNode(tag = "spectre.grandchildPopup")

                automator.click(automator.waitForNode(tag = "spectre.grandchildActionButton"))
                automator.waitForNode(text = "Grandchild clicks: 1")
                automator.waitForIdle()

                assertTrue(
                    automator.isPresent { findByTestTag("spectre.parentPopup") },
                    "Clicking inside a nested grandchild popup must not dismiss the parent popup",
                )
                assertTrue(
                    automator.isPresent { findByTestTag("spectre.childPopup") },
                    "Clicking inside a nested grandchild popup must not dismiss the child popup",
                )
                assertTrue(
                    automator.isPresent { findByTestTag("spectre.grandchildPopup") },
                    "Nested grandchild popup must remain open after clicking an internal action",
                )

                // Clicking the root owner window toggle button must close the entire popup hierarchy cleanly without
                // reopening.
                automator.click(openParentButton)
                automator.waitUntilGone(tag = "spectre.parentPopup", timeout = 10.seconds)
                automator.waitForIdle()
                assertFalse(
                    automator.isPresent { findByTestTag("spectre.parentPopup") },
                    "Clicking the root owner toggle button must close the nested popup hierarchy without reopening it",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `popup preserves caller LocalDensity and provides its own ComposePanel as LocalComponent`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val ownerComponentRef = AtomicReference<JComponent?>(null)
            val popupComponentRef = AtomicReference<JComponent?>(null)
            val popupDensityRef = AtomicReference<Density?>(null)
            val customDensity = Density(density = 2.5f, fontScale = 1.25f)

            val app = SpectreTestApplication {
                var popupVisible by remember { mutableStateOf(false) }
                ownerComponentRef.set(LocalComponent.current)

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DefaultButton(
                        onClick = { popupVisible = true },
                        modifier = Modifier.testTag("spectre.openContextPopup"),
                    ) {
                        Text("Open context popup")
                    }

                    if (popupVisible) {
                        CompositionLocalProvider(LocalDensity provides customDensity) {
                            PopupContainer(
                                onDismissRequest = { popupVisible = false },
                                horizontalAlignment = Alignment.Start,
                                popupProperties = PopupProperties(focusable = true),
                                modifier = Modifier.testTag("spectre.contextPopup"),
                            ) {
                                popupComponentRef.set(LocalComponent.current)
                                popupDensityRef.set(LocalDensity.current)
                                Text("Context popup content")
                            }
                        }
                    }
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                automator.click(automator.waitForNode(tag = "spectre.openContextPopup"))
                automator.waitForNode(tag = "spectre.contextPopup")
                automator.waitForIdle()

                assertEquals(customDensity, popupDensityRef.get(), "Popup must inherit caller LocalDensity")
                val ownerComponent = assertNotNull(ownerComponentRef.get())
                val popupComponent = assertNotNull(popupComponentRef.get())
                assertNotSame(
                    ownerComponent,
                    popupComponent,
                    "Popup content must receive the popup's own ComposePanel as LocalComponent, not the owner's",
                )
            } finally {
                app.stop()
            }
        }

    @Test
    fun `clicking a menu combo box with a selected item opens its popup`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val items = listOf("Cat", "Elephant", "Sun")
            val app = SpectreTestApplication {
                var selectedItem by remember { mutableIntStateOf(0) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuComboBox(
                        labelContent = { Text(items[selectedItem]) },
                        modifier = Modifier.width(200.dp).testTag("spectre.menuComboBox"),
                        content = {
                            items.forEachIndexed { index, item ->
                                selectableItem(selected = index == selectedItem, onClick = { selectedItem = index }) {
                                    Text(item)
                                }
                            }
                        },
                    )
                }
            }
            app.start()
            try {
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(app.awaitWindow()))

                val menuComboBox = automator.waitForNode(tag = "spectre.menuComboBox")
                automator.click(menuComboBox)
                automator.waitForNode(tag = COMBO_BOX_POPUP_TAG, timeout = 5.seconds)

                automator.click(automator.waitForNode(text = "Elephant"))
                automator.waitUntilGone(tag = COMBO_BOX_POPUP_TAG, timeout = 5.seconds)
                automator.waitForNode(text = "Elephant")

                // Reopen when a different item is selected.
                automator.click(automator.waitForNode(tag = "spectre.menuComboBox"))
                automator.waitForNode(tag = COMBO_BOX_POPUP_TAG, timeout = 5.seconds)
            } finally {
                app.stop()
            }
        }

    @Test
    fun `clicking a popup menu item does not make the popup dialog focusable or deactivate the owner window`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val app = SpectreTestApplication()
            app.start()
            val ownerDeactivated = AtomicBoolean(false)
            val windowListener =
                object : WindowAdapter() {
                    override fun windowDeactivated(e: WindowEvent?) {
                        ownerDeactivated.set(true)
                    }
                }
            var hostWindow: ComposeWindow? = null
            try {
                val window = app.awaitWindow()
                hostWindow = window
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(window))

                val menuButton = automator.waitForNode(tag = MENU_BUTTON_TAG)
                automator.click(menuButton)
                automator.waitForNode(tag = MENU_POPUP_TAG)
                automator.waitForIdle()
                SwingUtilities.invokeAndWait {}

                val popupDialog = window.ownedWindows.filterIsInstance<JDialog>().singleOrNull { it.isShowing }
                assertNotNull(popupDialog, "Expected a showing JDialog for the focusable popup menu")
                assertFalse(
                    popupDialog.focusableWindowState,
                    "Popup JDialog must keep focusableWindowState=false so clicking inside it cannot steal key window status",
                )
                assertFalse(popupDialog.isFocusableWindow, "Popup JDialog must not be a focusable window")

                window.addWindowListener(windowListener)
                ownerDeactivated.set(false)

                automator.click(automator.waitForNode(text = MENU_ITEM_TEXT))
                automator.waitUntilGone(tag = MENU_POPUP_TAG, timeout = 10.seconds)
                automator.waitForIdle()

                assertFalse(ownerDeactivated.get(), "Clicking a popup menu item must not deactivate the owner window")
            } finally {
                hostWindow?.removeWindowListener(windowListener)
                app.stop()
            }
        }

    @Test
    fun `keyboard navigation through nested popup menus and submenus routes to topmost menu and selects item`(): Unit =
        runSpectreTestWithCustomPopupRenderer {
            val selectedItem = AtomicReference("None")
            val app = SpectreTestApplication {
                var showMenu by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        DefaultButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("spectre.openSubmenuHierarchy"),
                        ) {
                            Text("Show Menu with Submenus")
                        }

                        if (showMenu) {
                            PopupMenu(
                                onDismissRequest = {
                                    showMenu = false
                                    true
                                },
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.testTag("spectre.rootMenuPopup"),
                            ) {
                                selectableItem(selected = false, onClick = { selectedItem.set("Direct Item") }) {
                                    Text("Direct Item")
                                }
                                submenu(
                                    submenu = {
                                        selectableItem(
                                            selected = false,
                                            onClick = { selectedItem.set("Submenu Item 1") },
                                        ) {
                                            Text("Submenu Item 1")
                                        }
                                        submenu(
                                            submenu = {
                                                selectableItem(
                                                    selected = false,
                                                    onClick = { selectedItem.set("Nested Item 1") },
                                                ) {
                                                    Text("Nested Item 1")
                                                }
                                            },
                                            content = { Text("Nested Submenu") },
                                        )
                                    },
                                    content = { Text("More Options") },
                                )
                            }
                        }
                    }
                }
            }
            app.start()
            try {
                val hostWindow = app.awaitWindow()
                val automator = ComposeAutomator.inProcess(RobotDriver.synthetic(hostWindow))

                val openButton = automator.waitForNode(tag = "spectre.openSubmenuHierarchy")
                automator.click(openButton)
                automator.waitForNode(tag = "spectre.rootMenuPopup")

                // Keep pointer on the owner window so all key events arrive at the owner window first.
                automator.moveTo(openButton)
                automator.waitForIdle()

                // Down -> focuses "Direct Item"; Down -> focuses "More Options"; Right -> opens first submenu.
                automator.pressKey(KeyEvent.VK_DOWN)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_DOWN)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_RIGHT)
                automator.waitForNode(text = "Submenu Item 1", timeout = 5.seconds)

                // Down -> focuses "Submenu Item 1"; Down -> focuses "Nested Submenu"; Right -> opens nested submenu.
                automator.pressKey(KeyEvent.VK_DOWN)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_DOWN)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_RIGHT)
                automator.waitForNode(text = "Nested Item 1", timeout = 5.seconds)

                // Left closes only the innermost nested submenu.
                automator.pressKey(KeyEvent.VK_LEFT)
                automator.waitUntilGone(text = "Nested Item 1", timeout = 5.seconds)
                assertTrue(
                    automator.isPresent { findByText("Submenu Item 1") },
                    "First submenu must remain open when Left closes the nested submenu",
                )

                // Left again closes the first submenu while keeping the root menu open.
                automator.pressKey(KeyEvent.VK_LEFT)
                automator.waitUntilGone(text = "Submenu Item 1", timeout = 5.seconds)
                assertTrue(
                    automator.isPresent { findByTestTag("spectre.rootMenuPopup") },
                    "Root menu must remain open when Left closes the first submenu",
                )

                // Up moves focus back to "Direct Item", and Space activates it and closes the root menu.
                automator.pressKey(KeyEvent.VK_UP)
                automator.waitForIdle()
                automator.pressKey(KeyEvent.VK_SPACE)
                automator.waitUntilGone(tag = "spectre.rootMenuPopup", timeout = 5.seconds)
                assertEquals("Direct Item", selectedItem.get())
            } finally {
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
