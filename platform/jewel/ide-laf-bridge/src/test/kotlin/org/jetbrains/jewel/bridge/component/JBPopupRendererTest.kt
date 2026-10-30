// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.component

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair as IjPair
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.BooleanFunction
import io.mockk.every
import io.mockk.mockk
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent as AWTKeyEvent
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.bridge.theme.readMenuStyle
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.component.MenuComboBox
import org.jetbrains.jewel.ui.component.PopupRenderer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/**
 * Contract and initial-bounds unit tests for [JBPopupRenderer]. Full interactive end-to-end tests that build a real
 * `JBPopup` in a running IDE live in the `jewel-bridge-e2e-tests` lane.
 */
@Suppress("LargeClass")
public class JBPopupRendererTest {
    @JvmField @Rule public val applicationRule: ApplicationRule = ApplicationRule()

    @JvmField @Rule public val disposableRule: DisposableRule = DisposableRule()

    @JvmField @Rule public val rule: ComposeContentTestRule = createComposeRule()

    @Test
    public fun `overrides every InputMode-aware Popup overload`() {
        assertEquals(
            "PopupRenderer is expected to declare exactly two InputMode-aware Popup overloads",
            2,
            inputModeAwareOverloads.size,
        )

        val inherited = inputModeAwareOverloads.filterNot { it.isOverriddenByBridge() }

        assertTrue(
            "JBPopupRenderer must declare its own override of every InputMode-aware PopupRenderer.Popup overload. An " +
                "inherited one falls through to the deprecated overloads, which drop the InputMode and end in an " +
                "error. Inherited: " +
                inherited.joinToString { it.toGenericString() },
            inherited.isEmpty(),
        )
    }

    @Test
    public fun `implements the windowShape-aware overload`() {
        val (withoutShape, windowShapeAware) = inputModeAwareOverloads.sortedBy { it.parameterCount }

        assertEquals(
            "The windowShape-aware overload is expected to take exactly one parameter more than the one without it",
            withoutShape.parameterCount + 1,
            windowShapeAware.parameterCount,
        )
        assertTrue(
            "The extra parameter is expected to be the IntSize -> Shape factory, but the overload is: " +
                windowShapeAware.toGenericString(),
            windowShapeAware.toGenericString().contains("IntSize") &&
                windowShapeAware.toGenericString().contains("java.awt.Shape"),
        )
        assertTrue(
            "JBPopupRenderer must implement the windowShape-aware overload, even though it ignores the shape: " +
                "JBPopup does not expose native window shaping",
            windowShapeAware.isOverriddenByBridge(),
        )
    }

    @Test
    public fun `popup is shown at measured screen coordinates and size on initial show`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val showEvents = installFakePopupFactory(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null
        val disposableEffectRuns = AtomicInteger(0)
        val disposableEffectDisposals = AtomicInteger(0)
        val launchedEffectRuns = AtomicInteger(0)
        var showPopup by mutableStateOf(true)

        rule.setContent {
            CompositionLocalProvider(
                LocalComponent provides owner,
                LocalDensity provides Density(JBUIScale.sysScale(owner)),
            ) {
                Box(modifier = Modifier.size(400.dp, 300.dp)) {
                    Box(modifier = Modifier.offset(50.dp, 60.dp).size(100.dp, 40.dp)) {
                        if (showPopup) {
                            JBPopupRenderer.Popup(
                                popupPositionProvider = BottomLeftPopupPositionProvider,
                                properties = PopupProperties(),
                                onDismissRequest = noOpDismiss,
                                onPreviewKeyEvent = null,
                                onKeyEvent = null,
                                cornerSize = ZeroCornerSize,
                            ) {
                                DisposableEffect(Unit) {
                                    disposableEffectRuns.incrementAndGet()
                                    onDispose { disposableEffectDisposals.incrementAndGet() }
                                }
                                LaunchedEffect(Unit) { launchedEffectRuns.incrementAndGet() }

                                Box(modifier = Modifier.size(160.dp, 90.dp))
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        assertTrue("Expected popup.showInScreenCoordinates to be called", showEvents.isNotEmpty())
        val (initialShowPoint, initialShowSize) = showEvents.first()
        assertEquals(
            "Popup must be shown directly at its computed screen coordinates, not (0, 0)",
            Point(350, 300),
            initialShowPoint,
        )
        assertEquals(
            "Popup must have its measured content size before initial show, not 1x1",
            Dimension(160, 90),
            initialShowSize,
        )
        assertEquals("DisposableEffect in popup content must run only once", 1, disposableEffectRuns.get())
        assertEquals("LaunchedEffect in popup content must run only once", 1, launchedEffectRuns.get())
        assertEquals("DisposableEffect must not dispose while popup is open", 0, disposableEffectDisposals.get())

        showPopup = false
        rule.waitForIdle()
        assertEquals("DisposableEffect must dispose once when popup closes", 1, disposableEffectDisposals.get())
    }

    @Test
    public fun `popup inside intrinsic size parent with scrollbar does not fail intrinsic measurement`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val showEvents = installFakePopupFactory(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(
                LocalComponent provides owner,
                LocalDensity provides Density(JBUIScale.sysScale(owner)),
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    Box(modifier = Modifier.size(120.dp, 32.dp)) {
                        JBPopupRenderer.Popup(
                            popupPositionProvider = BottomLeftPopupPositionProvider,
                            properties = PopupProperties(),
                            onDismissRequest = noOpDismiss,
                            onPreviewKeyEvent = null,
                            onKeyEvent = null,
                            cornerSize = ZeroCornerSize,
                        ) {
                            Box(modifier = Modifier.size(180.dp, 110.dp)) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(rememberScrollState()),
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        assertTrue("Expected popup.showInScreenCoordinates to be called", showEvents.isNotEmpty())
        val (initialShowPoint, initialShowSize) = showEvents.first()
        assertEquals(Point(300, 232), initialShowPoint)
        assertEquals(Dimension(180, 110), initialShowSize)
    }

    @Test
    public fun `ComposePanel attached to displayable invisible window measures content on preferredSize`() {
        assumeFalse("ComposePanel requires a non-headless AWT environment", GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            var measuredSize: Dimension? = null
            val wrapper =
                compose {
                    Layout(content = { Box(modifier = Modifier.size(140.dp, 75.dp)) }) { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        measuredSize = Dimension(placeable.width, placeable.height)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
                    as JewelComposePanelWrapper
            val window = JWindow()
            try {
                assertNull(
                    "Unattached ComposePanel must have null graphicsConfiguration before attachment",
                    wrapper.composePanel.graphicsConfiguration,
                )
                window.contentPane.add(wrapper)
                window.addNotify()
                assertFalse("Popup window must remain invisible during pre-measurement", window.isVisible)
                val preferredSize = wrapper.preferredSize
                assertNotNull("ComposePanel must measure its content on getPreferredSize()", measuredSize)
                assertEquals(Dimension(140, 75), preferredSize)
            } finally {
                window.dispose()
                wrapper.composePanel.dispose()
            }
        }
    }

    @Test
    public fun `modified escape does not dismiss popup or claim dismiss request`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        val dismissals = mutableListOf<InputMode>()
        val recordDismiss: (InputMode) -> Unit = { dismissals += it }

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
                        onDismissRequest = recordDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = null,
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()
        val keyEventHandler = checkNotNull(capture.keyEventHandler) { "Expected keyEventHandler to be registered" }

        for (modifierMask in
            listOf(
                AWTKeyEvent.SHIFT_DOWN_MASK,
                AWTKeyEvent.CTRL_DOWN_MASK,
                AWTKeyEvent.ALT_DOWN_MASK,
                AWTKeyEvent.META_DOWN_MASK,
            )) {
            val modifiedEscape =
                AWTKeyEvent(
                    owner,
                    AWTKeyEvent.KEY_PRESSED,
                    0L,
                    modifierMask,
                    AWTKeyEvent.VK_ESCAPE,
                    AWTKeyEvent.CHAR_UNDEFINED,
                )
            assertFalse(
                "Modified Escape ($modifierMask) must not be claimed as a dismiss request",
                keyEventHandler.`fun`(modifiedEscape),
            )
        }
        rule.waitForIdle()
        assertTrue("Modified Escape must not dismiss the popup", dismissals.isEmpty())

        val unmodifiedEscape =
            AWTKeyEvent(owner, AWTKeyEvent.KEY_PRESSED, 0L, 0, AWTKeyEvent.VK_ESCAPE, AWTKeyEvent.CHAR_UNDEFINED)
        assertTrue("Unmodified Escape must be claimed by a focusable popup", keyEventHandler.`fun`(unmodifiedEscape))
        rule.waitForIdle()
        assertEquals(listOf(InputMode.Keyboard), dismissals)
    }

    @Test
    public fun `key event handler routes keys to popup target between preview and bubble callbacks`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        val callOrder = mutableListOf<String>()
        var consumeInPreview = false
        var consumeInTarget = false
        var consumeInKeyCallback = false
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = {
                            callOrder += "preview"
                            consumeInPreview
                        },
                        onKeyEvent = {
                            callOrder += "key"
                            consumeInKeyCallback
                        },
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()
        val preferredComponent =
            checkNotNull(capture.preferredFocusableComponent) { "Expected preferred focusable component" }
        val innerTarget = JPanel()
        innerTarget.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(event: AWTKeyEvent) {
                    callOrder += "target"
                    if (consumeInTarget) {
                        event.consume()
                    }
                }
            }
        )
        preferredComponent.add(innerTarget, 0)

        val keyEventHandler = checkNotNull(capture.keyEventHandler) { "Expected keyEventHandler to be registered" }
        val keyDownA = AWTKeyEvent(owner, AWTKeyEvent.KEY_PRESSED, 0L, 0, AWTKeyEvent.VK_A, 'a')

        // 1. Neither consumes: preview -> target -> key, returns false
        callOrder.clear()
        assertFalse(keyEventHandler.`fun`(keyDownA))
        assertEquals(listOf("preview", "target", "key"), callOrder)

        // 2. Target consumes: preview -> target, key callback is skipped, returns true
        callOrder.clear()
        consumeInTarget = true
        assertTrue(keyEventHandler.`fun`(keyDownA))
        assertEquals(listOf("preview", "target"), callOrder)

        // 3. Preview consumes: preview only, target and key callback are skipped, returns true
        callOrder.clear()
        consumeInPreview = true
        assertTrue(keyEventHandler.`fun`(keyDownA))
        assertEquals(listOf("preview"), callOrder)

        // 4. Key callback consumes after target declines: preview -> target -> key, returns true
        callOrder.clear()
        consumeInPreview = false
        consumeInTarget = false
        consumeInKeyCallback = true
        assertTrue(keyEventHandler.`fun`(keyDownA))
        assertEquals(listOf("preview", "target", "key"), callOrder)
    }

    @Test
    public fun `non-focusable popup does not route keys to popup target`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        var targetReceivedKey = false
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = false),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = null,
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()
        val preferredComponent =
            checkNotNull(capture.preferredFocusableComponent) { "Expected preferred focusable component" }
        val innerTarget = JPanel()
        innerTarget.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(event: AWTKeyEvent) {
                    targetReceivedKey = true
                    event.consume()
                }
            }
        )
        preferredComponent.add(innerTarget, 0)

        val keyEventHandler = checkNotNull(capture.keyEventHandler) { "Expected keyEventHandler to be registered" }
        val keyDownA = AWTKeyEvent(owner, AWTKeyEvent.KEY_PRESSED, 0L, 0, AWTKeyEvent.VK_A, 'a')
        assertFalse(keyEventHandler.`fun`(keyDownA))
        assertFalse("Non-focusable popup must not route keys to its inner target", targetReceivedKey)
    }

    @Test
    public fun `non-focusable popup with text field leaves typed keys to owner and keeps popup field empty`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        val ownerFieldState = TextFieldState("")
        val popupFieldState = TextFieldState("")
        val ownerFocusRequester = FocusRequester()
        val popupFocusRequester = FocusRequester()
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(LocalComponent provides owner) {
                    Column {
                        LaunchedEffect(Unit) { ownerFocusRequester.requestFocus() }
                        TextField(
                            state = ownerFieldState,
                            modifier =
                                Modifier.width(120.dp)
                                    .focusRequester(ownerFocusRequester)
                                    .testTag("bridge.ownerTextField"),
                        )

                        JBPopupRenderer.Popup(
                            popupPositionProvider = BottomLeftPopupPositionProvider,
                            properties = PopupProperties(focusable = false),
                            onDismissRequest = noOpDismiss,
                            onPreviewKeyEvent = null,
                            onKeyEvent = null,
                            cornerSize = ZeroCornerSize,
                        ) {
                            LaunchedEffect(Unit) { popupFocusRequester.requestFocus() }
                            TextField(
                                state = popupFieldState,
                                modifier =
                                    Modifier.width(120.dp)
                                        .focusRequester(popupFocusRequester)
                                        .testTag("bridge.popupTextField"),
                            )
                        }
                    }
                }
            }
        }

        rule.waitForIdle()
        val keyEventHandler = checkNotNull(capture.keyEventHandler) { "Expected keyEventHandler to be registered" }
        for (char in "abc") {
            val code = char.uppercaseChar().code
            assertFalse(keyEventHandler.`fun`(AWTKeyEvent(owner, AWTKeyEvent.KEY_PRESSED, 0L, 0, code, char)))
            assertFalse(
                keyEventHandler.`fun`(AWTKeyEvent(owner, AWTKeyEvent.KEY_TYPED, 0L, 0, AWTKeyEvent.VK_UNDEFINED, char))
            )
            assertFalse(keyEventHandler.`fun`(AWTKeyEvent(owner, AWTKeyEvent.KEY_RELEASED, 0L, 0, code, char)))
        }

        rule.onNodeWithTag("bridge.ownerTextField").performTextInput("abc")
        rule.waitForIdle()

        assertEquals("Owner text field must receive the typed keys", "abc", ownerFieldState.text.toString())
        assertEquals("Non-focusable popup text field must stay empty", "", popupFieldState.text.toString())
    }

    @Test
    public fun `popup content receives popup ComposePanel as LocalComponent during pre-measurement`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        var observedLocalComponent: JComponent? = null
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = null,
                        cornerSize = ZeroCornerSize,
                    ) {
                        observedLocalComponent = LocalComponent.current
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()
        assertNull(
            "Owner must not leak as LocalComponent inside popup content",
            observedLocalComponent.takeIf { it === owner },
        )
        assertSame(capture.preferredFocusableComponent, observedLocalComponent)
    }

    @Test
    public fun `popup pre-measurement does not steal focus from host anchor when popup content requests focus`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val showEvents = installFakePopupFactory(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null
        val hostFocusRequester = FocusRequester()
        var popupVisible by mutableStateOf(false)
        var hostFocused by mutableStateOf(false)

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(
                    modifier =
                        Modifier.size(120.dp, 32.dp)
                            .focusRequester(hostFocusRequester)
                            .onFocusChanged { state ->
                                hostFocused = state.isFocused
                                if (!state.isFocused) {
                                    popupVisible = false
                                }
                            }
                            .focusable()
                ) {
                    if (popupVisible) {
                        JBPopupRenderer.Popup(
                            popupPositionProvider = BottomLeftPopupPositionProvider,
                            properties = PopupProperties(focusable = true),
                            onDismissRequest = noOpDismiss,
                            onPreviewKeyEvent = null,
                            onKeyEvent = null,
                            cornerSize = ZeroCornerSize,
                        ) {
                            val itemFocusRequester = remember { FocusRequester() }
                            Box(
                                modifier = Modifier.size(100.dp, 60.dp).focusRequester(itemFocusRequester).focusable()
                            ) {
                                DisposableEffect(Unit) {
                                    itemFocusRequester.requestFocus()
                                    onDispose {}
                                }
                            }
                        }
                    }
                }
            }
        }

        rule.runOnIdle {
            hostFocusRequester.requestFocus()
            popupVisible = true
        }
        rule.waitForIdle()

        assertTrue("Host anchor must keep Compose focus during popup pre-measurement", hostFocused)
        assertTrue("Popup must remain visible after pre-measurement", popupVisible)
        assertTrue("Popup must be shown at its pre-measured bounds", showEvents.isNotEmpty())
    }

    @Test
    public fun `focusable popup disables native focus request and registers owner focus chain`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = null,
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()

        assertEquals(true, capture.focusable)
        assertEquals(
            "Focusable JBPopup must not request native AWT focus on show or click so the host window stays active",
            false,
            capture.requestFocus,
        )
        assertEquals(listOf<Component>(owner), capture.focusOwners)

        val focusOnClickInsideField =
            checkNotNull(capture.contentComponent) { "Expected popup content component" }
                .javaClass
                .getDeclaredField("focusOnClickInside")
                .apply { isAccessible = true }
        assertEquals(
            "Popup JewelComposePanelWrapper must set focusOnClickInside=false so clicking inside does not steal AWT focus",
            false,
            focusOnClickInsideField.getBoolean(capture.contentComponent),
        )
    }

    @Test
    public fun `non-focusable popup does not register focus owners or request native focus`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val capture = installFakePopupFactoryCapture(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides owner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = false),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = null,
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }
                }
            }
        }

        rule.waitForIdle()

        assertEquals(false, capture.focusable)
        assertEquals(false, capture.requestFocus)
        assertEquals(emptyList<Component>(), capture.focusOwners)
    }

    @Test
    public fun `nested focusable popup receives key events ahead of parent focusable popup`() {
        val rootOwner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val parentPopupOwner = FakeShowingOwnerComponent(screenLocation = Point(300, 232))
        val childPopupOwner = FakeShowingOwnerComponent(screenLocation = Point(400, 232))
        cacheZeroScreenInsets(parentPopupOwner.graphicsConfiguration)
        cacheZeroScreenInsets(childPopupOwner.graphicsConfiguration)
        val capture = installFakePopupFactoryCapture(rootOwner)
        val noOpDismiss: ((InputMode) -> Unit)? = null
        val receivedBy = mutableListOf<String>()
        var childVisible by mutableStateOf(true)
        var grandchildVisible by mutableStateOf(true)

        rule.setContent {
            CompositionLocalProvider(LocalComponent provides rootOwner) {
                Box(modifier = Modifier.size(120.dp, 32.dp)) {
                    JBPopupRenderer.Popup(
                        popupPositionProvider = BottomLeftPopupPositionProvider,
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = noOpDismiss,
                        onPreviewKeyEvent = null,
                        onKeyEvent = {
                            receivedBy += "parent"
                            true
                        },
                        cornerSize = ZeroCornerSize,
                    ) {
                        Box(modifier = Modifier.size(100.dp, 60.dp))
                    }

                    if (childVisible) {
                        CompositionLocalProvider(
                            LocalComponent provides parentPopupOwner,
                            LocalRootPopupOwner provides rootOwner,
                        ) {
                            JBPopupRenderer.Popup(
                                popupPositionProvider = BottomLeftPopupPositionProvider,
                                properties = PopupProperties(focusable = true),
                                onDismissRequest = noOpDismiss,
                                onPreviewKeyEvent = null,
                                onKeyEvent = {
                                    receivedBy += "child"
                                    true
                                },
                                cornerSize = ZeroCornerSize,
                            ) {
                                Box(modifier = Modifier.size(100.dp, 60.dp))
                            }
                        }
                    }

                    if (grandchildVisible) {
                        CompositionLocalProvider(
                            LocalComponent provides childPopupOwner,
                            LocalRootPopupOwner provides rootOwner,
                        ) {
                            JBPopupRenderer.Popup(
                                popupPositionProvider = BottomLeftPopupPositionProvider,
                                properties = PopupProperties(focusable = true),
                                onDismissRequest = noOpDismiss,
                                onPreviewKeyEvent = null,
                                onKeyEvent = {
                                    receivedBy += "grandchild"
                                    true
                                },
                                cornerSize = ZeroCornerSize,
                            ) {
                                Box(modifier = Modifier.size(100.dp, 60.dp))
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        assertEquals("Expected parent, child, and grandchild popups to be created", 3, capture.createdPopups.size)
        assertEquals(listOf<Component>(rootOwner), capture.createdPopups[0].focusOwners)
        assertEquals(listOf<Component>(parentPopupOwner, rootOwner), capture.createdPopups[1].focusOwners)
        assertEquals(listOf<Component>(childPopupOwner, rootOwner), capture.createdPopups[2].focusOwners)

        val parentKeyHandler =
            checkNotNull(capture.createdPopups[0].keyEventHandler) { "Expected parent keyEventHandler" }
        val keyDownDownArrow =
            AWTKeyEvent(rootOwner, AWTKeyEvent.KEY_PRESSED, 0L, 0, AWTKeyEvent.VK_DOWN, AWTKeyEvent.CHAR_UNDEFINED)

        assertTrue(parentKeyHandler.`fun`(keyDownDownArrow))
        assertEquals(
            "When StackingPopupDispatcherImpl invokes the parent popup handler, it must route to the deepest active grandchild popup",
            listOf("grandchild"),
            receivedBy,
        )

        receivedBy.clear()
        rule.runOnIdle { grandchildVisible = false }
        rule.waitForIdle()

        assertTrue(parentKeyHandler.`fun`(keyDownDownArrow))
        assertEquals(
            "After the grandchild popup closes, the child popup must handle key events",
            listOf("child"),
            receivedBy,
        )

        receivedBy.clear()
        rule.runOnIdle { childVisible = false }
        rule.waitForIdle()

        assertTrue(parentKeyHandler.`fun`(keyDownDownArrow))
        assertEquals(
            "After the child popup closes, the parent popup must handle key events again",
            listOf("parent"),
            receivedBy,
        )
    }

    @Test
    public fun `bridge menu style uses focused background for pressed menu items`() {
        val itemColors = readMenuStyle().colors.itemColors
        assertEquals(itemColors.backgroundFocused, itemColors.backgroundPressed)
    }

    @Test
    public fun `clicking a menu combo box with a selected item keeps the popup open`() {
        val previousFlag = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = true
        try {
            val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
            val capture = installFakePopupFactoryCapture(owner)
            val items = listOf("Cat", "Elephant", "Sun")

            rule.setContent {
                SwingBridgeTheme {
                    CompositionLocalProvider(
                        LocalComponent provides owner,
                        LocalPopupRenderer provides JBPopupRenderer,
                    ) {
                        MenuComboBox(
                            labelContent = { Text(items[0]) },
                            modifier = Modifier.width(200.dp).testTag("bridge.menuComboBox"),
                            content = {
                                items.forEachIndexed { index, item ->
                                    selectableItem(selected = index == 0, onClick = {}) { Text(item) }
                                }
                            },
                        )
                    }
                }
            }

            rule.waitForIdle()
            rule.onNodeWithTag("bridge.menuComboBox").performClick()
            rule.waitForIdle()

            assertEquals("Expected MenuComboBox popup to be created", 1, capture.createdPopups.size)
            assertTrue("Expected MenuComboBox popup to be shown", capture.showEvents.isNotEmpty())
            assertFalse(
                "MenuComboBox popup with a selected item must stay open after clicking the combo box",
                capture.createdPopups.single().disposed,
            )
        } finally {
            JewelFlags.useCustomPopupRenderer = previousFlag
        }
    }

    /**
     * The overloads declared by [PopupRenderer] itself whose `onDismissRequest` reports the dismissal `InputMode`,
     * minus the synthetic bridge the Compose compiler emits to carry the `windowShape` default value. The deprecated
     * overloads without an `InputMode` only exist for renderers written before it, which the bridge is not.
     */
    private val inputModeAwareOverloads: List<Method>
        get() =
            PopupRenderer::class.java.declaredMethods.filter {
                it.name == "Popup" && !it.isSynthetic && it.genericParameterTypes[2].typeName.contains("InputMode")
            }

    /**
     * Whether [JBPopupRenderer] declares this overload itself. A compiler-generated bridge does not count: depending on
     * the JVM default-method mode, Kotlin emits one in the implementing class for every inherited interface method, and
     * it would make an override that is missing from the source look present.
     */
    private fun Method.isOverriddenByBridge(): Boolean =
        runCatching { JBPopupRenderer::class.java.getDeclaredMethod(name, *parameterTypes) }
            .getOrNull()
            ?.let { !it.isBridge && !it.isSynthetic } == true

    private class FakePopupCapture(val showEvents: MutableList<Pair<Point, Dimension>>) {
        var contentComponent: JComponent? = null
        var preferredFocusableComponent: JComponent? = null
        var keyEventHandler: BooleanFunction<in AWTKeyEvent>? = null
        var focusable: Boolean? = null
        var requestFocus: Boolean? = null
        var focusOwners: List<Component>? = null
        var disposed: Boolean = false
        val createdPopups: MutableList<FakePopupCapture> = mutableListOf()
    }

    private fun installFakePopupFactory(owner: Component): List<Pair<Point, Dimension>> =
        installFakePopupFactoryCapture(owner).showEvents

    private fun installFakePopupFactoryCapture(owner: Component): FakePopupCapture {
        cacheZeroScreenInsets(owner.graphicsConfiguration)
        val rootCapture = FakePopupCapture(mutableListOf())
        val jbPopupInterface = Class.forName("com.intellij.openapi.ui.popup.JBPopup")

        val factory = mockk<JBPopupFactory>(relaxed = true)
        every { factory.createComponentPopupBuilder(any(), any()) } answers
            {
                val popupCapture = FakePopupCapture(rootCapture.showEvents)
                popupCapture.contentComponent = firstArg()
                popupCapture.preferredFocusableComponent = secondArg()
                rootCapture.contentComponent = popupCapture.contentComponent
                rootCapture.preferredFocusableComponent = popupCapture.preferredFocusableComponent
                rootCapture.createdPopups += popupCapture

                val setSizeEvents = mutableListOf<Pair<Point, Dimension>>()
                var attachContentBeforeMeasuring = false
                var preMeasureWindow: JWindow? = null
                var disposed = false
                val popupProxy =
                    Proxy.newProxyInstance(jbPopupInterface.classLoader, arrayOf(jbPopupInterface)) {
                        proxy,
                        method,
                        args ->
                        when (method.name) {
                            "setSize" -> {
                                if (args != null && args.size == 2) {
                                    val point = args[0] as Point
                                    val size = args[1] as Dimension
                                    setSizeEvents += Point(point) to Dimension(size)
                                }
                                null
                            }
                            "showInScreenCoordinates" -> {
                                val point = args?.get(1) as Point
                                val content =
                                    popupCapture.contentComponent ?: error("Popup content component was not captured")
                                val shouldAttachBeforeMeasuring =
                                    attachContentBeforeMeasuring ||
                                        content.getClientProperty("AttachContentBeforeMeasuring") == true
                                if (shouldAttachBeforeMeasuring && !GraphicsEnvironment.isHeadless()) {
                                    val window =
                                        JWindow().apply {
                                            contentPane.add(content)
                                            addNotify()
                                        }
                                    preMeasureWindow = window
                                    Disposer.register(disposableRule.disposable) { window.dispose() }
                                }
                                val currentPreferredSize = content.preferredSize
                                val effectivePoint = setSizeEvents.lastOrNull()?.first ?: point
                                val effectiveSize = setSizeEvents.lastOrNull()?.second ?: currentPreferredSize
                                rootCapture.showEvents += Point(effectivePoint) to Dimension(effectiveSize)
                                null
                            }
                            "cancel" -> {
                                preMeasureWindow?.dispose()
                                preMeasureWindow = null
                                disposed = true
                                popupCapture.disposed = true
                                rootCapture.disposed = true
                                null
                            }
                            "isDisposed" -> disposed
                            "isVisible" -> false
                            "hashCode" -> System.identityHashCode(proxy)
                            "equals" -> proxy === args?.get(0)
                            "toString" -> "FakeJBPopup"
                            else -> null
                        }
                    }

                lateinit var builderProxy: ComponentPopupBuilder
                builderProxy =
                    Proxy.newProxyInstance(
                        ComponentPopupBuilder::class.java.classLoader,
                        arrayOf(ComponentPopupBuilder::class.java),
                    ) { proxy, method, args ->
                        when (method.name) {
                            "setAttachContentBeforeMeasuring" -> {
                                attachContentBeforeMeasuring = args?.get(0) as? Boolean ?: false
                                builderProxy
                            }
                            "setFocusable" -> {
                                val value = args?.get(0) as? Boolean
                                popupCapture.focusable = value
                                rootCapture.focusable = value
                                builderProxy
                            }
                            "setRequestFocus" -> {
                                val value = args?.get(0) as? Boolean
                                popupCapture.requestFocus = value
                                rootCapture.requestFocus = value
                                builderProxy
                            }
                            "setFocusOwners" -> {
                                @Suppress("UNCHECKED_CAST") val owners = (args?.get(0) as? Array<Component>)?.toList()
                                popupCapture.focusOwners = owners
                                rootCapture.focusOwners = owners
                                builderProxy
                            }
                            "setKeyEventHandler" -> {
                                @Suppress("UNCHECKED_CAST")
                                val handler = args?.get(0) as? BooleanFunction<in AWTKeyEvent>
                                popupCapture.keyEventHandler = handler
                                rootCapture.keyEventHandler = handler
                                builderProxy
                            }
                            "createPopup" -> popupProxy
                            "hashCode" -> System.identityHashCode(proxy)
                            "equals" -> proxy === args?.get(0)
                            "toString" -> "FakeComponentPopupBuilder"
                            else -> builderProxy
                        }
                    } as ComponentPopupBuilder

                builderProxy
            }
        ApplicationManager.getApplication()
            .replaceService(JBPopupFactory::class.java, factory, disposableRule.disposable)

        return rootCapture
    }

    private fun cacheZeroScreenInsets(graphicsConfiguration: GraphicsConfiguration) {
        val insetCacheField = ScreenUtil::class.java.getDeclaredField("insetCache").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val insetCache =
            insetCacheField.get(null) as? MutableMap<GraphicsConfiguration, IjPair<Insets, Long>>
                ?: error("ScreenUtil.insetCache must be enabled via ide.cache.screen.insets")
        synchronized(insetCache) {
            insetCache[graphicsConfiguration] = IjPair.create(Insets(0, 0, 0, 0), System.currentTimeMillis())
        }
        Disposer.register(disposableRule.disposable) {
            synchronized(insetCache) { insetCache.remove(graphicsConfiguration) }
        }
    }

    private companion object {
        init {
            System.setProperty("ide.cache.screen.insets", "true")
            System.setProperty("ide.insets.cache.timeout", "3600000")
        }
    }
}

private object BottomLeftPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom)
}

private class FakeShowingOwnerComponent(private val screenLocation: Point) : JPanel() {
    private val configuration = FakeGraphicsConfiguration()

    init {
        setLocation(screenLocation)
    }

    override fun isShowing(): Boolean = true

    override fun getLocationOnScreen(): Point = Point(screenLocation)

    override fun getGraphicsConfiguration(): GraphicsConfiguration = configuration
}

private class FakeGraphicsConfiguration : GraphicsConfiguration() {
    private val graphicsDevice =
        object : GraphicsDevice() {
            override fun getType(): Int = TYPE_RASTER_SCREEN

            override fun getIDstring(): String = "test-screen"

            override fun getConfigurations(): Array<GraphicsConfiguration> = arrayOf(this@FakeGraphicsConfiguration)

            override fun getDefaultConfiguration(): GraphicsConfiguration = this@FakeGraphicsConfiguration
        }

    override fun getDevice(): GraphicsDevice = graphicsDevice

    override fun getColorModel(): ColorModel = ColorModel.getRGBdefault()

    override fun getColorModel(transparency: Int): ColorModel = ColorModel.getRGBdefault()

    override fun getDefaultTransform(): AffineTransform =
        if (GraphicsEnvironment.isHeadless()) {
            AffineTransform()
        } else {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform
        }

    override fun getNormalizingTransform(): AffineTransform = AffineTransform()

    override fun getBounds(): Rectangle = Rectangle(0, 0, 1920, 1080)
}
