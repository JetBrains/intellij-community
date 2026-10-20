// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Popup as ComposePopup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jetbrains.JBR
import java.awt.AWTError
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FocusTraversalPolicy
import java.awt.HeadlessException
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent as AWTKeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JDialog
import javax.swing.JWindow
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.floor
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.ui.component.PopupRenderer
import org.jetbrains.skiko.hostOs

private val focusablePopupKeyRoutingOrder = CopyOnWriteArrayList<Window>()

/**
 * Selects the Swing window class that [JDialogRenderer] creates for a popup.
 * - [Dialog]: Created for focusable popups (`PopupProperties.focusable = true`). A [JDialog] has a macOS `NSWindow`
 *   peer that can become the key window and receive keyboard focus.
 * - [Window]: Created for non-focusable popups (`PopupProperties.focusable = false`). A [JWindow] has a non-activating
 *   macOS `NSWindow` peer. Showing a [JWindow] does not cause macOS to re-evaluate the key window or steal focus from
 *   another open focusable popup.
 */
internal enum class PopupWindowKind {
    Dialog,
    Window,
}

internal fun popupWindowKind(properties: PopupProperties): PopupWindowKind =
    if (properties.focusable) PopupWindowKind.Dialog else PopupWindowKind.Window

private sealed interface PopupWindow {
    val awtWindow: Window
    val rootPaneContainer: RootPaneContainer

    fun show(properties: PopupProperties, onAfterShow: () -> Unit)

    fun dispose() {
        awtWindow.isVisible = false
        awtWindow.dispose()
    }
}

private class JDialogPopupWindow(private val dialog: JDialog) : PopupWindow {
    override val awtWindow: Window
        get() = dialog

    override val rootPaneContainer: RootPaneContainer
        get() = dialog

    override fun show(properties: PopupProperties, onAfterShow: () -> Unit) {
        // Keep the popup dialog non-focusable at the native window level so showing or clicking inside the popup
        // never steals active/key-window status from the owner window. Keyboard events for focusable popups are
        // routed from the active owner window to the popup's ComposeScene by the AWTEventListener below.
        dialog.focusableWindowState = false
        dialog.isAutoRequestFocus = false
        dialog.isVisible = true
        onAfterShow()
    }
}

private class JWindowPopupWindow(private val window: JWindow) : PopupWindow {
    override val awtWindow: Window
        get() = window

    override val rootPaneContainer: RootPaneContainer
        get() = window

    override fun show(properties: PopupProperties, onAfterShow: () -> Unit) {
        window.isVisible = true
        onAfterShow()
    }
}

private fun <T> T.applyTransparentPopupChrome() where T : Window, T : RootPaneContainer {
    rootPane.isOpaque = false
    background = TRANSPARENT_WITH_WINDOWS_HACK
    contentPane.background = TRANSPARENT_WITH_WINDOWS_HACK
    if (hostOs.isMacOS) {
        rootPane.putClientProperty("Window.shadow", true)
    }
}

/**
 * A popup renderer implementation that uses heavyweight Swing windows ([JDialog] and [JWindow]) to display popups in a
 * Compose UI.
 *
 * This renderer creates a [JDialog] for focusable popups and a [JWindow] for non-focusable popups to host Compose
 * content, allowing integration between Swing and Compose UI hierarchies.
 *
 * Note that this renderer requires you to meet at least one of the following conditions:
 * - Be running on JBR (JetBrains Runtime);
 * - Having set the 'compose.interop.blending' system property;
 *     - For more information about its implications, check the official documentation available at
 *       https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-swing-interoperability.html#experimental-interop-blending
 * - Forcing the default Swing background to be transparent;
 *     - For more information about how to do this, check the official documentation available at
 *       https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/UIManager.html
 *
 * If none of the conditions are met, the popup will be rendered using the standard Compose API to prevent rendering any
 * possible issues.
 */
internal object JDialogRenderer : PopupRenderer {
    @Suppress("OVERRIDE_DEPRECATION")
    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: ((inputMode: InputMode) -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        cornerSize: CornerSize,
        content: @Composable () -> Unit,
    ) {
        Popup(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            onDismissRequest = onDismissRequest,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            cornerSize = cornerSize,
            windowShape = null,
            content = content,
        )
    }

    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: ((inputMode: InputMode) -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        cornerSize: CornerSize,
        windowShape: ((IntSize) -> java.awt.Shape)?,
        content: @Composable () -> Unit,
    ) {
        val isJBREnvironment = remember { JBR.isAvailable() && JBR.isRoundedCornersManagerSupported() }
        val supportBlending = remember {
            System.getProperty("compose.interop.blending", "false").toBoolean() ||
                UIManager.getColor("Panel.background").alpha == 0
        }

        val currentComponent = LocalComponent.currentOrNull // Avoiding break the app if LocalComponent is not provided
        val window = remember(currentComponent) { SwingUtilities.getWindowAncestor(currentComponent) }

        LaunchedEffect(window) {
            if (!isJBREnvironment && !supportBlending) {
                JewelLogger.getInstance("PopupRenderer")
                    .warn(
                        "JDialogRenderer is enabled, but the requirements to use it are not meet. " +
                            "Falling back to the default renderer. For more information, check for the KDoc of the " +
                            "org.jetbrains.jewel.intui.standalone.popup.JDialogRenderer object."
                    )
            }

            if (window == null) {
                JewelLogger.getInstance("PopupRenderer")
                    .warn("LocalComponent was not provided. Falling back to the default renderer.")
            }
        }

        if ((!isJBREnvironment && !supportBlending) || window == null) {
            ComposePopup(
                popupPositionProvider = popupPositionProvider,
                properties = properties,
                onDismissRequest = onDismissRequest?.let { dismiss -> { dismiss(InputMode.Touch) } },
                onPreviewKeyEvent = onPreviewKeyEvent,
                onKeyEvent = onKeyEvent,
                content = content,
            )
        } else {
            JPopupImpl(
                window = window,
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = onDismissRequest,
                properties = properties,
                onPreviewKeyEvent = onPreviewKeyEvent,
                onKeyEvent = onKeyEvent,
                cornerSize = cornerSize,
                blendingEnabled = supportBlending,
                windowShape = windowShape,
                content = content,
            )
        }
    }
}

@Composable
private fun JPopupImpl(
    window: Window,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: ((inputMode: InputMode) -> Unit)?,
    properties: PopupProperties,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
    onKeyEvent: ((KeyEvent) -> Boolean)?,
    cornerSize: CornerSize,
    blendingEnabled: Boolean,
    windowShape: ((IntSize) -> java.awt.Shape)?,
    content: @Composable () -> Unit,
) {
    val popupDensity = LocalDensity.current
    val component = LocalComponent.current

    val currentContent by rememberUpdatedState(content)
    val currentPopupPositionProvider by rememberUpdatedState(popupPositionProvider)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
    val currentProperties by rememberUpdatedState(properties)
    val currentCornerSize by rememberUpdatedState(cornerSize)
    val currentWindowShape by rememberUpdatedState(windowShape)

    val compositionLocalContext by rememberUpdatedState(currentCompositionLocalContext)

    var parentBoundsInRoot by remember { mutableStateOf<IntRect?>(null) }

    val popupWindow: PopupWindow =
        remember(window, properties.focusable) {
            when (popupWindowKind(properties)) {
                PopupWindowKind.Dialog ->
                    JDialogPopupWindow(
                        JDialog(window).apply {
                            isUndecorated = true
                            applyTransparentPopupChrome()
                        }
                    )

                PopupWindowKind.Window ->
                    JWindowPopupWindow(
                        JWindow(window).apply {
                            type = Window.Type.POPUP
                            if (isWaylandToolkit) {
                                rootPane.putClientProperty("wlawt.popup_position_unconstrained", true)
                            }
                            applyTransparentPopupChrome()
                            isFocusable = false
                            focusableWindowState = false
                            isAlwaysOnTop = false
                            isAutoRequestFocus = false
                        }
                    )
            }
        }
    var popupRectangle by remember(popupWindow) { mutableStateOf<Rectangle?>(null) }
    val outsidePressTracker = remember(popupWindow) { PopupOutsidePressTracker() }
    var dispatchedToPopupScene by remember(popupWindow) { mutableStateOf(false) }
    var unconsumedByPopupScene by remember(popupWindow) { mutableStateOf(false) }

    fun updateWindowShape(size: IntSize) {
        val awtWindow = popupWindow.awtWindow
        val shapeProvider = currentWindowShape
        if (shapeProvider != null) {
            if (blendingEnabled) {
                // When blending is active, the window is already in
                // java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT mode
                // (per-pixel alpha). Calling Window.setShape() would switch it to
                // PERPIXEL_TRANSPARENT (hard pixel clip), breaking antialiasing at the edges.
                // Compose's own drawing + transparent background is sufficient.
                return
            }
            // Without blending, fall back to Window.setShape() to at least
            // clip the rectangular window boundary to the custom shape outline.
            // Note: this uses PERPIXEL_TRANSPARENT mode, which has known
            // antialiasing limitations at concave corners (e.g. arrow junction).
            val logicalSize =
                IntSize(
                    floor(size.width / awtWindow.density()).toInt(),
                    floor(size.height / awtWindow.density()).toInt(),
                )
            try {
                awtWindow.shape = shapeProvider(logicalSize)
            } catch (_: UnsupportedOperationException) {
                applyRoundedCorners(awtWindow, currentCornerSize, size)
            }
            return
        }

        awtWindow.shape = null
        if (blendingEnabled) {
            // If any of the blending logic is enabled, we don't need to use JBR APIs
            // to set the rounded corners and fix the background.
            return
        }

        applyRoundedCorners(awtWindow, currentCornerSize, size)
    }

    fun onPopupMeasured(position: IntOffset, size: IntSize) {
        val newRectangle = Rectangle(position.x, position.y, size.width, size.height)
        if (popupRectangle != newRectangle) {
            outsidePressTracker.onPopupRepositioned()
        }
        popupRectangle = newRectangle
        updateWindowShape(size)
    }

    val composePanel =
        remember(popupWindow) {
            val awtWindow = popupWindow.awtWindow
            ComposePanel(renderSettings = DEFAULT_RENDER_SETTINGS).apply {
                isClearFocusOnMouseDownEnabled = false
                layout = null
                isOpaque = false
                background = TRANSPARENT_WITH_WINDOWS_HACK
                preferredSize = Dimension(1, 1)
                focusTraversalPolicy = NoFocusTraversalPolicy

                setContent {
                    val parentBounds = parentBoundsInRoot ?: return@setContent

                    Layout(
                        content = {
                            ProvideValuesFromOtherContext(compositionLocalContext) {
                                CompositionLocalProvider(
                                    LocalComponent provides this@apply,
                                    LocalDensity provides popupDensity,
                                ) {
                                    currentContent()
                                }
                            }
                        },
                        modifier =
                            Modifier.semantics { popup() }
                                .onPreviewKeyEvent {
                                    dispatchedToPopupScene = true
                                    unconsumedByPopupScene = false
                                    false
                                }
                                .onKeyEvent {
                                    unconsumedByPopupScene = true
                                    false
                                },
                        measurePolicy =
                            remember(currentPopupPositionProvider, parentBounds, awtWindow) {
                                JPopupMeasurePolicy(
                                    window = awtWindow,
                                    popupPositionProvider = currentPopupPositionProvider,
                                    parentBoundsInRoot = parentBounds,
                                    onMeasure = ::onPopupMeasured,
                                )
                            },
                    )
                }
            }
        }

    val currentParentBounds = parentBoundsInRoot
    Layout(
        content = {},
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
                if (component.isShowing) {
                    childCoordinates.parentCoordinates?.let {
                        parentBoundsInRoot = it.boundsInRoot().roundToIntRect().fromRelativeToScreen(component)
                    }
                }
            },
        measurePolicy = { _, _ -> layout(0, 0) {} },
    )

    LaunchedEffect(popupWindow, properties) {
        if (popupWindow is JDialogPopupWindow) {
            popupWindow.awtWindow.isFocusable = false
            popupWindow.awtWindow.focusableWindowState = false
            popupWindow.awtWindow.isAlwaysOnTop = properties.dismissOnClickOutside
        }
    }

    LaunchedEffect(popupWindow, windowShape, cornerSize) {
        val currentRect = popupRectangle ?: return@LaunchedEffect
        updateWindowShape(IntSize(currentRect.width, currentRect.height))
    }

    val rectValue = popupRectangle
    var keyboardFocusRequested by remember(popupWindow) { mutableStateOf(false) }
    LaunchedEffect(popupWindow, currentParentBounds, rectValue) {
        val awtWindow = popupWindow.awtWindow
        if (currentParentBounds != null && popupRectangle == null) {
            if (!awtWindow.isDisplayable) {
                awtWindow.addNotify()
            }
            composePanel.preferredSize = null
            composePanel.preferredSize
        }
        val rectangle = popupRectangle?.withDensity(awtWindow.density()) ?: return@LaunchedEffect
        val location = rectangle.location.fromCurrentScreenToGlobal(window)
        composePanel.preferredSize = rectangle.size
        awtWindow.bounds = Rectangle(location, rectangle.size)
        if (!awtWindow.isVisible) {
            val shouldRequestFocus = currentProperties.focusable && !keyboardFocusRequested
            if (shouldRequestFocus) {
                keyboardFocusRequested = true
            }
            popupWindow.show(currentProperties) {
                if (shouldRequestFocus) {
                    composePanel.requestFocusInWindow()
                }
            }
        }
    }

    LaunchedEffect(blendingEnabled, windowShape) {
        if (windowShape != null && !blendingEnabled) {
            myLogger()
                .warn(
                    "windowShape was provided but blending is not enabled; falling back to PERPIXEL_TRANSPARENT mode. " +
                        "This may cause antialiasing limitations at concave corners."
                )
        }
    }

    DisposableEffect(popupWindow, composePanel) {
        val awtWindow = popupWindow.awtWindow
        val listener = AWTEventListener { event ->
            when (event) {
                is MouseEvent -> {
                    if (event.id == MouseEvent.MOUSE_PRESSED) {
                        val eventWindow = SwingUtilities.getWindowAncestor(event.component)
                        outsidePressTracker.onPress(
                            button = event.button,
                            isOwnerOutsidePress =
                                currentProperties.focusable &&
                                    currentProperties.dismissOnClickOutside &&
                                    !awtWindow.isAncestorOf(event.component) &&
                                    !awtWindow.isInWindowOwnerChainOf(eventWindow) &&
                                    !awtWindow.bounds.contains(event.locationOnScreen) &&
                                    (eventWindow == window || eventWindow?.isInWindowOwnerChainOf(awtWindow) == true),
                        )
                    }
                    val matchingOwnerOutsidePressObserved =
                        event.id == MouseEvent.MOUSE_RELEASED && outsidePressTracker.onRelease(event.button)
                    if (shouldDismissOnClick(event, awtWindow, currentProperties, ownerWindow = window)) {
                        currentOnDismissRequest?.invoke(InputMode.Touch)
                    } else if (
                        shouldDeferDismissOnMouseReleased(
                            event = event,
                            dialog = awtWindow,
                            properties = currentProperties,
                            ownerWindow = window,
                            matchingOwnerOutsidePressObserved = matchingOwnerOutsidePressObserved,
                        )
                    ) {
                        // For focusable popups, WINDOW_LOST_FOCUS is skipped when the user clicks in the parent window.
                        // Deferring dismissal to MOUSE_RELEASED via invokeLater lets Compose process the click first.
                        SwingUtilities.invokeLater { currentOnDismissRequest?.invoke(InputMode.Touch) }
                    }
                }

                is WindowEvent -> {
                    // Both predicates accept the same event when the popup holds the focus and the user leaves
                    // the application. The application switch must win. Touch lets a hovered submenu item veto
                    // the dismissal through the guard in MenuSubmenuItem. That guard settles the hover and click
                    // race on an item. It must not keep a popup open after the application goes away.
                    if (shouldDismissOnApplicationSwitch(event, awtWindow, currentProperties)) {
                        currentOnDismissRequest?.invoke(InputMode.Keyboard)
                    } else if (shouldDismissOnFocusLoss(event, awtWindow, currentProperties, ownerWindow = window)) {
                        currentOnDismissRequest?.invoke(InputMode.Touch)
                    }
                }

                is AWTKeyEvent -> {
                    if (event.isConsumed) return@AWTEventListener

                    val composeEvent = event.toComposeKeyEvent()
                    val eventWindow = SwingUtilities.getWindowAncestor(event.component) ?: event.component as? Window
                    // Focusable popups do not necessarily own native focus immediately (see the JEWEL-1276
                    // focus-stealing workaround above), so their key events can arrive from an ancestor owner window.
                    // AWT can also deliver Escape to a window that owns no popup at all; in that case, the topmost
                    // visible focusable popup still claims the dismiss request.
                    val topOwnedFocusableTarget =
                        focusablePopupKeyRoutingOrder.lastOrNull { candidate ->
                            candidate.isVisible &&
                                (eventWindow == candidate || eventWindow?.isInWindowOwnerChainOf(candidate) == true)
                        }
                    val isTopFocusableTarget =
                        currentProperties.focusable &&
                            (topOwnedFocusableTarget
                                ?: focusablePopupKeyRoutingOrder.lastOrNull {
                                    it.isVisible && composeEvent.isDismissRequest()
                                }) == awtWindow
                    val eventOnPopup =
                        eventWindow == awtWindow && (!currentProperties.focusable || isTopFocusableTarget)
                    val eventOnOwner =
                        eventWindow != awtWindow &&
                            isTopFocusableTarget &&
                            (eventWindow?.isInWindowOwnerChainOf(awtWindow) == true ||
                                (topOwnedFocusableTarget == null && composeEvent.isDismissRequest()))
                    if (
                        !PopupEventRouting.shouldDispatchKeyEventToPopup(
                            popupFocusable = currentProperties.focusable,
                            popupVisible = awtWindow.isVisible,
                            popupActive = eventOnPopup,
                            ownerActive = eventOnOwner,
                        )
                    ) {
                        return@AWTEventListener
                    }

                    val consumedByPreviewCallback = currentOnPreviewKeyEvent?.invoke(composeEvent) == true
                    val keyEventTarget = composePanel.findPopupKeyEventTarget()
                    val consumedByTarget =
                        if (
                            !consumedByPreviewCallback &&
                                (event.component !== keyEventTarget || composeEvent.isDismissRequest())
                        ) {
                            dispatchedToPopupScene = false
                            unconsumedByPopupScene = false
                            val routedEvent =
                                AWTKeyEvent(
                                    keyEventTarget,
                                    event.id,
                                    event.`when`,
                                    event.modifiersEx,
                                    event.keyCode,
                                    event.keyChar,
                                    event.keyLocation,
                                )
                            keyEventTarget.dispatchPopupKeyEventToTargetListeners(routedEvent)
                            if (composeEvent.key == Key.Escape) {
                                // ComposeSceneMediator's BackNavigationEventInput consumes every Escape KeyDown
                                // even when nothing in the popup's ComposeScene handled the event.
                                dispatchedToPopupScene && !unconsumedByPopupScene
                            } else {
                                routedEvent.isConsumed
                            }
                        } else {
                            false
                        }
                    val consumedByKeyCallback =
                        !consumedByPreviewCallback &&
                            !consumedByTarget &&
                            currentOnKeyEvent?.invoke(composeEvent) == true

                    val consumed = consumedByPreviewCallback || consumedByTarget || consumedByKeyCallback
                    val dismissRequest = currentOnDismissRequest
                    // Whether Escape dismisses is the popup's declared policy; a null callback just means we have
                    // no way to act on it, so we must not claim the key either.
                    val dismissed =
                        !consumed &&
                            composeEvent.isDismissRequest() &&
                            currentProperties.dismissOnBackPress &&
                            dismissRequest != null

                    if (dismissed) {
                        dismissRequest.invoke(InputMode.Keyboard)
                    }
                    // A focusable popup swallows Escape whether or not it dismissed, matching Compose. A
                    // non-focusable one must let a key it did not act on reach whatever owns it.
                    val claimsDismissRequest = composeEvent.isDismissRequest() && currentProperties.focusable
                    val handledDismissRequest = dismissed || claimsDismissRequest
                    val claimsOwnerRoutedKey =
                        eventOnOwner &&
                            PopupEventRouting.shouldConsumeOwnerActiveKeyAfterRedispatch(
                                redispatchedEventConsumed = consumed,
                                isDismissRequest = handledDismissRequest,
                                isModifiedDismissRequest = composeEvent.isModifiedDismissRequest(),
                            )
                    if (consumed || handledDismissRequest || claimsOwnerRoutedKey) {
                        event.consume()
                    }
                }
            }
        }

        Toolkit.getDefaultToolkit()
            .addAWTEventListener(
                listener,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK or AWTEvent.KEY_EVENT_MASK,
            )
        onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    DisposableEffect(popupWindow, composePanel) {
        popupWindow.rootPaneContainer.contentPane = composePanel
        if (currentProperties.focusable) {
            focusablePopupKeyRoutingOrder.remove(popupWindow.awtWindow)
            focusablePopupKeyRoutingOrder.add(popupWindow.awtWindow)
        }

        onDispose {
            focusablePopupKeyRoutingOrder.remove(popupWindow.awtWindow)
            popupWindow.dispose()
            composePanel.dispose()
        }
    }
}

private fun shouldDismissOnClick(
    event: MouseEvent,
    dialog: Window,
    properties: PopupProperties,
    ownerWindow: Window,
): Boolean {
    if (!properties.dismissOnClickOutside || event.id != MouseEvent.MOUSE_PRESSED) return false

    val eventWindow = SwingUtilities.getWindowAncestor(event.component)
    return PopupEventRouting.shouldDismissOnMousePress(
        popupFocusable = properties.focusable,
        popupActive = dialog.isActive,
        eventInPopupOrOwnedChild = dialog.isAncestorOf(event.component) || dialog.isInWindowOwnerChainOf(eventWindow),
        eventInsidePopupBounds = dialog.bounds.contains(event.locationOnScreen),
        eventComponentInOwnerWindow = eventWindow == ownerWindow || eventWindow?.isInWindowOwnerChainOf(dialog) == true,
    )
}

private fun shouldDeferDismissOnMouseReleased(
    event: MouseEvent,
    dialog: Window,
    properties: PopupProperties,
    ownerWindow: Window,
    matchingOwnerOutsidePressObserved: Boolean,
): Boolean {
    if (event.id != MouseEvent.MOUSE_RELEASED || !properties.focusable || !properties.dismissOnClickOutside) {
        return false
    }
    val eventWindow = SwingUtilities.getWindowAncestor(event.component)
    return PopupEventRouting.shouldDeferDismissOnMouseReleased(
        popupFocusable = properties.focusable,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        isMouseReleasedEvent = true,
        matchingOwnerOutsidePressObserved = matchingOwnerOutsidePressObserved,
        eventComponentIsPopupDescendant =
            dialog.isAncestorOf(event.component) || dialog.isInWindowOwnerChainOf(eventWindow),
        eventInsidePopupBounds = dialog.bounds.contains(event.locationOnScreen),
        eventComponentInOwnerWindow = eventWindow == ownerWindow || eventWindow?.isInWindowOwnerChainOf(dialog) == true,
    )
}

private fun shouldDismissOnApplicationSwitch(event: WindowEvent, dialog: Window, properties: PopupProperties): Boolean =
    properties.dismissOnClickOutside &&
        event.id == WindowEvent.WINDOW_LOST_FOCUS &&
        event.oppositeWindow == null &&
        (event.window == dialog || event.window?.isInWindowOwnerChainOf(dialog) == true)

private fun shouldDismissOnFocusLoss(
    event: WindowEvent,
    dialog: Window,
    properties: PopupProperties,
    ownerWindow: Window,
): Boolean {
    val eventWindow = event.window ?: return false
    if (event.id != WindowEvent.WINDOW_LOST_FOCUS || !properties.focusable || !properties.dismissOnClickOutside) {
        return false
    }
    if (eventWindow != dialog && !eventWindow.isInWindowOwnerChainOf(dialog)) {
        return false
    }
    return PopupEventRouting.shouldDismissOnFocusLoss(
        dismissOnClickOutside = true,
        popupFocusable = true,
        isFocusLostEvent = true,
        focusLostWindowIsPopup = true,
        oppositeWindowInPopupOrOwnedChild =
            event.oppositeWindow == dialog ||
                dialog.isAncestorOf(event.oppositeWindow) ||
                dialog.isInWindowOwnerChainOf(event.oppositeWindow),
        oppositeWindowIsPopupOwner =
            event.oppositeWindow == ownerWindow || event.oppositeWindow?.isInWindowOwnerChainOf(dialog) == true,
    )
}

/** Returns true when this window is a direct or transitive owner of [other] in the AWT window owner chain. */
internal fun Window.isInWindowOwnerChainOf(other: Window?): Boolean {
    var current: Window? = other?.owner
    while (current != null) {
        if (current == this) return true
        current = current.owner
    }
    return false
}

/** Prevents focus from moving outside the popup via focus traversal keys. */
private object NoFocusTraversalPolicy : FocusTraversalPolicy() {
    override fun getFirstComponent(aContainer: Container?): Component? = null

    override fun getLastComponent(aContainer: Container?): Component? = null

    override fun getDefaultComponent(aContainer: Container?): Component? = null

    override fun getComponentAfter(aContainer: Container?, aComponent: Component?): Component? = null

    override fun getComponentBefore(aContainer: Container?, aComponent: Component?): Component? = null
}

private class JPopupMeasurePolicy(
    private val window: Window,
    private val popupPositionProvider: PopupPositionProvider,
    private val parentBoundsInRoot: IntRect,
    private val onMeasure: (IntOffset, IntSize) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val screenSize: IntSize = IntSize.screenSize(window)

        val relaxedConstraints =
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = screenSize.width, maxHeight = screenSize.height)

        val placeables = measurables.fastMap { it.measure(relaxedConstraints) }

        val contentSize =
            IntSize(
                width = placeables.fastMaxBy { it.width }?.width ?: constraints.minWidth,
                height = placeables.fastMaxBy { it.height }?.height ?: constraints.minHeight,
            )

        val position =
            popupPositionProvider.calculatePosition(
                anchorBounds = parentBoundsInRoot,
                windowSize = screenSize,
                layoutDirection = layoutDirection,
                popupContentSize = contentSize,
            )

        onMeasure(position, contentSize)

        return layout(contentSize.width, contentSize.height) { placeables.fastForEach { it.place(0, 0) } }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0
}

private fun applyRoundedCorners(dialog: Window, cornerSize: CornerSize, size: IntSize) {
    if (cornerSize == ZeroCornerSize) return
    JBR.getRoundedCornersManager()
        .setRoundedCorners(dialog, cornerSize.toPx(size.toSize(), Density(dialog.density())) / dialog.density())
}

// Based on implementation from JBUIScale and ScreenUtil
private fun IntSize.Companion.screenSize(window: Component): IntSize {
    val windowConfiguration = window.graphicsConfiguration.device.defaultConfiguration

    val screenSize = windowConfiguration.bounds
    val scale = windowConfiguration.defaultTransform.scaleX

    return IntSize((screenSize.width * scale).fastRoundToInt(), (screenSize.height * scale).fastRoundToInt())
}

/**
 * Updates the rectangle values to be relative to the current density. Needs to use floor() conversion here to avoid
 * rounding up pixel sizes that cause a visual glitch
 */
private fun Rectangle.withDensity(density: Float): Rectangle =
    Rectangle(
        floor(x / density).toInt(),
        floor(y / density).toInt(),
        floor(width / density).toInt(),
        floor(height / density).toInt(),
    )

/** Returns the screen density of the component's current monitor. */
private fun Component.density(): Float =
    graphicsConfiguration.device.defaultConfiguration.defaultTransform.scaleX.toFloat()

/**
 * As mentioned in the `locationOnDisplay` function, getting a component location can return negative values. But for
 * the popup position calculation, we need to convert the point to the relative position in the component's current
 * monitor.
 *
 * This function converts the point relative to the component's current monitor screen coordinates.
 *
 * @return An `IntRect` representing the rectangle in the component's current monitor screen coordinates to be used in
 *   the `popupPositionProvider.calculatePosition` call.
 */
private fun IntRect.fromRelativeToScreen(window: Component): IntRect {
    val density = window.density()
    val ownerLocation = window.locationOnDisplay()

    return IntRect(
        left = (ownerLocation.x * density).fastRoundToInt() + left,
        top = (ownerLocation.y * density).fastRoundToInt() + top,
        right = (ownerLocation.x * density).fastRoundToInt() + right,
        bottom = (ownerLocation.y * density).fastRoundToInt() + bottom,
    )
}

/**
 * Calculates a window's location relative to the top-left corner of the screen it is currently on. This is useful in
 * multi-monitor setups where getting the location can return negative values for screens positioned to the left of or
 * above the primary display.
 *
 * @return A Point object containing the x and y coordinates relative to the component's current monitor.
 */
private fun Component.locationOnDisplay(): Point {
    val globalLocation = locationOnScreen
    val gc = graphicsConfiguration
    val screenBounds = gc.bounds

    val relativeX = globalLocation.x - screenBounds.x
    val relativeY = globalLocation.y - screenBounds.y

    return Point(relativeX, relativeY)
}

/**
 * Same as `fromRelativeToScreen`, but performs the inverse operation. Converts the point relative to the component's
 * current monitor screen coordinates to the global screen coordinates.
 *
 * @return A `Point` representing the point in the component's current monitor screen coordinates, to be used in the
 *   JDialog position update.
 */
private fun Point.fromCurrentScreenToGlobal(window: Window): Point {
    val ownerLocation = window.locationOnDisplay()
    val point = Point(x - ownerLocation.x, y - ownerLocation.y)
    SwingUtilities.convertPointToScreen(point, window)
    return point
}

private val isWaylandToolkit: Boolean by lazy {
    try {
        Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.wl.WLToolkit"
    } catch (_: HeadlessException) {
        false
    } catch (_: AWTError) {
        false
    }
}

private val <T> CompositionLocal<T>.currentOrNull
    @Composable get() = customRunCatching { current }.getOrNull()

// see https://issuetracker.google.com/issues/417989445
//     https://issuetracker.google.com/issues/449904737
// according to Google folks, invoking @Composable callables inside try-catch-finally leads to runtime crashes,
// but the affected code was already here before me,
// and I am but one Kotlin QA engineer who's completely clueless about this codebase otherwise
private inline fun <R> customRunCatching(block: () -> R): Result<R> {
    @Suppress("TooGenericExceptionCaught")
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

private val AWTKeyEvent.keyLocationForCompose
    get() = if (keyLocation == KEY_LOCATION_UNKNOWN) KEY_LOCATION_STANDARD else keyLocation

@OptIn(InternalComposeUiApi::class)
private fun AWTKeyEvent.toComposeKeyEvent(): KeyEvent =
    KeyEvent(
        key = Key(nativeKeyCode = keyCode, nativeKeyLocation = keyLocationForCompose),
        type =
            when (id) {
                KEY_PRESSED -> KeyEventType.KeyDown
                KEY_RELEASED -> KeyEventType.KeyUp
                else -> KeyEventType.Unknown
            },
        codePoint = keyChar.code,
        isCtrlPressed = isControlDown,
        isMetaPressed = isMetaDown,
        isAltPressed = isAltDown,
        isShiftPressed = isShiftDown,
        nativeEvent = this,
    )

private val TRANSPARENT_WITH_WINDOWS_HACK = if (hostOs.isWindows) Color(0, 0, 0, 1) else Color(0, 0, 0, 0)

private val DEFAULT_RENDER_SETTINGS
    get() = if (hostOs.isWindows) RenderSettings.SwingGraphics() else ComposePanel.DefaultRenderSettings

/**
 * When inheriting from another context, we need to ensure that values already provided in the current context are not
 * overridden.
 */
@Composable
private fun ProvideValuesFromOtherContext(context: CompositionLocalContext, content: @Composable () -> Unit) {
    val existingContext = currentCompositionLocalContext
    CompositionLocalProvider(context) { CompositionLocalProvider(existingContext, content) }
}
