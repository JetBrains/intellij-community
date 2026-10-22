package org.jetbrains.jewel.bridge.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.KeyEvent as AWTKeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.ui.component.PopupRenderer

internal val LocalRootPopupOwner = staticCompositionLocalOf<Component?> { null }

private class FocusablePopupKeyEntry(
    val rootOwner: Component,
    @Volatile var isActive: () -> Boolean = { false },
    @Volatile var handleKeyEvent: (AWTKeyEvent) -> Boolean = { false },
)

private val focusablePopupKeyRoutingOrder = CopyOnWriteArrayList<FocusablePopupKeyEntry>()

internal object JBPopupRenderer : PopupRenderer {
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
        // JBPopup does not expose native window shaping, so windowShape is ignored.
        JBPopup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            content = content,
        )
    }
}

@Composable
private fun JBPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: ((inputMode: InputMode) -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val currentPopupPositionProvider by rememberUpdatedState(popupPositionProvider)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val currentProperties by rememberUpdatedState(properties)

    val owner = LocalComponent.current
    val rootOwner = LocalRootPopupOwner.current ?: owner
    val compositionLocalContext = currentCompositionLocalContext

    val parentBoundsInRoot = remember { mutableStateOf<IntRect?>(null) }
    val popupRectangle = remember { mutableStateOf<Rectangle?>(null) }
    var isPopupShown by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf<Boolean?>(null) }
    var dismissedWith by remember { mutableStateOf(InputMode.Touch) }
    LaunchedEffect(isVisible) {
        if (isVisible == false) {
            currentOnDismissRequest?.invoke(dismissedWith)
        }
    }

    var useHostLayoutFallback by remember {
        mutableStateOf(
            GraphicsEnvironment.isHeadless() && !java.lang.Boolean.getBoolean("compose.swing.render.on.graphics")
        )
    }
    var dispatchedToPopupScene by remember { mutableStateOf(false) }
    var unconsumedByPopupScene by remember { mutableStateOf(false) }

    val jewelComposePanelWrapper = remember {
        compose(
            focusOnClickInside = false,
            config = {
                // Initial placeholder dimensions before pre-measurement updates preferredSize.
                preferredSize = Dimension(1, 1)
            },
            content = {
                val parentBounds = parentBoundsInRoot.value ?: return@compose
                if (useHostLayoutFallback) return@compose

                Layout(
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
                    content = {
                        ProvideValuesFromOtherContext(compositionLocalContext) {
                            CompositionLocalProvider(LocalRootPopupOwner provides rootOwner) { currentContent.value() }
                        }
                    },
                    measurePolicy =
                        remember(currentPopupPositionProvider, parentBounds) {
                            JBPopupMeasurePolicy(
                                popupPositionProvider = currentPopupPositionProvider,
                                screenSize = IntSize.fromScreenSize(owner),
                                parentBoundsInWindow = parentBounds,
                                onMeasure = { position, size ->
                                    popupRectangle.value = Rectangle(position.x, position.y, size.width, size.height)
                                },
                            )
                        },
                )
            },
        )
            as JewelComposePanelWrapper
    }

    val currentParentBounds = parentBoundsInRoot.value
    Layout(
        content = {
            if (useHostLayoutFallback && currentParentBounds != null) {
                ProvideValuesFromOtherContext(compositionLocalContext) {
                    CompositionLocalProvider(
                        LocalComponent provides jewelComposePanelWrapper.composePanel,
                        LocalRootPopupOwner provides rootOwner,
                    ) {
                        currentContent.value()
                    }
                }
            }
        },
        modifier =
            Modifier.focusProperties {
                    canFocus = false
                    onEnter = { cancelFocusChange() }
                }
                .focusTarget()
                .onGloballyPositioned { childCoordinates ->
                    childCoordinates.parentCoordinates?.let {
                        if (owner.isShowing) {
                            parentBoundsInRoot.value = it.boundsInRoot().roundToIntRect().fromRelativeToScreen(owner)
                        }
                    }
                },
        measurePolicy =
            remember(useHostLayoutFallback, currentPopupPositionProvider, currentParentBounds, owner) {
                if (!useHostLayoutFallback || currentParentBounds == null) {
                    MeasurePolicy { _, _ -> layout(0, 0) {} }
                } else {
                    JBPopupMeasurePolicy(
                        popupPositionProvider = currentPopupPositionProvider,
                        screenSize = IntSize.fromScreenSize(owner),
                        parentBoundsInWindow = currentParentBounds,
                        placeContent = false,
                        onMeasure = { position, size ->
                            popupRectangle.value = Rectangle(position.x, position.y, size.width, size.height)
                        },
                    )
                }
            },
    )

    val handleOwnKeyEvent: (AWTKeyEvent) -> Boolean = { event ->
        val composeEvent = event.toComposeKeyEvent()
        val consumedByPreviewCallback = currentOnPreviewKeyEvent?.invoke(composeEvent) == true
        val keyEventTarget = jewelComposePanelWrapper.composePanel.findPopupKeyEventTarget()
        val needsTargetRouting = event.component !== keyEventTarget || composeEvent.isDismissRequest()
        val consumedByTarget =
            if (!consumedByPreviewCallback && currentProperties.focusable && needsTargetRouting) {
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
            !consumedByPreviewCallback && !consumedByTarget && currentOnKeyEvent?.invoke(composeEvent) == true
        val consumed = consumedByPreviewCallback || consumedByTarget || consumedByKeyCallback
        // Whether Escape dismisses is the popup's declared policy, not something inferred from the shape
        // of its callbacks. A null callback means we have no way to dismiss, so we must not claim the key.
        val dismissed =
            !consumed &&
                composeEvent.isDismissRequest() &&
                currentProperties.dismissOnBackPress &&
                currentOnDismissRequest != null

        if (dismissed) {
            dismissedWith = InputMode.Keyboard
            isVisible = false
        }
        // A focusable popup swallows Escape whether or not it dismissed, matching Compose. A
        // non-focusable one must let a key it did not act on reach whatever owns it.
        val claimsDismissRequest = composeEvent.isDismissRequest() && currentProperties.focusable
        consumed || dismissed || claimsDismissRequest
    }

    val currentHandleOwnKeyEvent by rememberUpdatedState(handleOwnKeyEvent)
    val focusableKeyEntry = remember(rootOwner) { FocusablePopupKeyEntry(rootOwner = rootOwner) }

    val popup: JBPopup = remember {
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(jewelComposePanelWrapper, jewelComposePanelWrapper.composePanel)
            .setFocusable(currentProperties.focusable)
            // Keep native AWT focus on the owner window/component so showing or clicking the popup never
            // deactivates the parent window or steals focus from anchor components (such as MenuComboBox).
            // Registering the owner focus chain allows StackingPopupDispatcherImpl.getFocusedPopup() to
            // continue routing keyboard events to focusable popups while the owner holds AWT focus.
            .setRequestFocus(false)
            .setFocusOwners(if (currentProperties.focusable) ownerFocusChain(owner, rootOwner) else emptyArray())
            .setCancelOnClickOutside(currentProperties.dismissOnClickOutside)
            .setCancelOnWindowDeactivation(currentProperties.dismissOnClickOutside)
            .setLocateWithinScreenBounds(false)
            // AbstractPopup cancels itself on Escape whenever the handler below declines the event, which would
            // close the popup behind Compose's back and leave the two out of sync. Take sole ownership instead.
            .setCancelKeyEnabled(false)
            .setKeyEventHandler { event ->
                val topEntry =
                    if (currentProperties.focusable) {
                        focusablePopupKeyRoutingOrder.lastOrNull { entry ->
                            entry.rootOwner === rootOwner && entry.isActive()
                        }
                    } else {
                        null
                    }
                if (topEntry != null && topEntry !== focusableKeyEntry) {
                    topEntry.handleKeyEvent(event)
                } else {
                    currentHandleOwnKeyEvent(event)
                }
            }
            .addListener(
                object : JBPopupListener {
                    override fun beforeShown(event: LightweightWindowEvent) {
                        updateComposePanelWindowParent(jewelComposePanelWrapper.composePanel)
                        isVisible = true
                    }

                    override fun onClosed(event: LightweightWindowEvent) {
                        isVisible = false
                    }
                }
            )
            .createPopup()
    }
    focusableKeyEntry.isActive = {
        currentProperties.focusable && isPopupShown && isVisible != false && !popup.isDisposed
    }
    focusableKeyEntry.handleKeyEvent = { event -> currentHandleOwnKeyEvent(event) }

    // The builder above only captures the properties as they were on first composition. AbstractPopup reads these
    // flags live on every event, so pushing changes keeps a popup whose properties vary (a ComboBox suppressing
    // click-outside dismissal while its chevron is hovered) in step without recreating the native window.
    LaunchedEffect(popup, currentProperties) {
        val abstractPopup = popup as? AbstractPopup ?: return@LaunchedEffect
        abstractPopup.setCancelOnClickOutside(currentProperties.dismissOnClickOutside)
        abstractPopup.setCancelOnWindowDeactivation(currentProperties.dismissOnClickOutside)
        abstractPopup.popupWindow?.configureNonFocusablePopupWindow()
    }

    val rectValue = popupRectangle.value
    LaunchedEffect(currentParentBounds, rectValue, useHostLayoutFallback) {
        if (!useHostLayoutFallback && currentParentBounds != null && popupRectangle.value == null) {
            val measured = preMeasureComposePanel(jewelComposePanelWrapper.composePanel, owner)
            if (!measured || popupRectangle.value == null) {
                useHostLayoutFallback = true
                return@LaunchedEffect
            }
        }
        val rectangle = popupRectangle.value ?: return@LaunchedEffect
        if (popup.isDisposed || !owner.isShowing) return@LaunchedEffect
        val globalLocation = rectangle.location.fromCurrentScreenToGlobal(owner)
        val globalSize = rectangle.size.withSystemDensity(owner)
        jewelComposePanelWrapper.preferredSize = globalSize
        jewelComposePanelWrapper.composePanel.preferredSize = globalSize
        popup.setSize(globalLocation, globalSize)
        if (!isPopupShown) {
            isPopupShown = true
            popup.showInScreenCoordinates(owner, globalLocation)
            (popup as? AbstractPopup)?.popupWindow?.configureNonFocusablePopupWindow()
        }
    }

    DisposableEffect(popup) {
        focusablePopupKeyRoutingOrder.remove(focusableKeyEntry)
        focusablePopupKeyRoutingOrder.add(focusableKeyEntry)
        onDispose {
            focusablePopupKeyRoutingOrder.remove(focusableKeyEntry)
            popup.cancel()
            if (!jewelComposePanelWrapper.composePanel.isDisplayable) {
                jewelComposePanelWrapper.composePanel.dispose()
            }
        }
    }
}

private fun Window.configureNonFocusablePopupWindow() {
    focusableWindowState = false
    isFocusable = false
    isAutoRequestFocus = false
}

internal fun ownerFocusChain(owner: Component, rootOwner: Component = owner): Array<Component> =
    buildList {
            fun addWithWindowAncestors(component: Component) {
                if (!contains(component)) add(component)
                var window: Window? = SwingUtilities.getWindowAncestor(component) ?: (component as? Window)
                while (window != null) {
                    if (!contains(window)) add(window)
                    window = window.owner
                }
            }
            addWithWindowAncestors(owner)
            if (rootOwner !== owner) {
                addWithWindowAncestors(rootOwner)
            }
        }
        .toTypedArray()

private class JBPopupMeasurePolicy(
    private val popupPositionProvider: PopupPositionProvider,
    private val screenSize: IntSize,
    private val parentBoundsInWindow: IntRect,
    private val placeContent: Boolean = true,
    private val onMeasure: (IntOffset, IntSize) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (!placeContent && measurables.isEmpty()) {
            return layout(0, 0) {}
        }

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
                anchorBounds = parentBoundsInWindow,
                windowSize = screenSize,
                layoutDirection = layoutDirection,
                popupContentSize = contentSize,
            )

        onMeasure(position, contentSize)

        if (!placeContent) {
            return layout(0, 0) {}
        }

        return layout(contentSize.width, contentSize.height) { placeables.fastForEach { it.place(0, 0) } }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0
}

/** Update the sizes of the popup to match the system scale factor. */
private fun Dimension.withSystemDensity(owner: Component): Dimension {
    val density = JBUIScale.sysScale(owner)

    return Dimension((width / density).fastRoundToInt(), (height / density).fastRoundToInt())
}

/**
 * As mentioned in the `locationOnDisplay` function, getLocationOnScreen() can return negative values. But for the popup
 * position calculation, we need to convert the point to the relative position in the component's current monitor.
 *
 * This function converts the point relative to the component's current monitor screen coordinates.
 *
 * @return An `IntRect` representing the rectangle in the component's current monitor screen coordinates to be used in
 *   the `popupPositionProvider.calculatePosition` call.
 */
private fun IntRect.fromRelativeToScreen(owner: Component): IntRect {
    val density = JBUIScale.sysScale(owner)
    val ownerLocation = owner.locationOnDisplay()

    return IntRect(
        left = (ownerLocation.x * density).fastRoundToInt() + left,
        top = (ownerLocation.y * density).fastRoundToInt() + top,
        right = (ownerLocation.x * density).fastRoundToInt() + right,
        bottom = (ownerLocation.y * density).fastRoundToInt() + bottom,
    )
}

/**
 * Same as `fromRelativeToScreen`, but performs the inverse operation. Converts the point relative to the component's
 * current monitor screen coordinates to the global screen coordinates.
 *
 * @return A `Point` representing the point in the component's current monitor screen coordinates, to be used in the
 *   JBPopup position update.
 */
private fun Point.fromCurrentScreenToGlobal(owner: Component): Point {
    val density = JBUIScale.sysScale(owner)
    val ownerLocation = owner.locationOnDisplay()

    val point =
        Point(x - (ownerLocation.x * density).fastRoundToInt(), y - (ownerLocation.y * density).fastRoundToInt())

    return RelativePoint(owner, Point((point.x / density).fastRoundToInt(), (point.y / density).fastRoundToInt()))
        .screenPoint
}

/**
 * Gets the screen size of the monitor that the component is currently on. It already takes into account the system
 * scale factor and returns the value in pixels.
 *
 * For the popup to be displayed correctly, it is important to use the screen size, and not only the size of the
 * component itself, as the popup may be larger than the component.
 */
private fun IntSize.Companion.fromScreenSize(owner: Component): IntSize =
    ScreenUtil.getScreenRectangle(owner).let {
        val density = JBUIScale.sysScale(owner)
        IntSize((it.width * density).fastRoundToInt(), (it.height * density).fastRoundToInt())
    }

/**
 * Calculates a component's location relative to the top-left corner of the screen it is currently on. This is useful in
 * multi-monitor setups where getLocationOnScreen() can return negative values for screens positioned to the left of or
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

internal fun Component.findPopupKeyEventTarget(): Component = findPopupKeyEventTargetOrNull() ?: this

internal fun Component.dispatchPopupKeyEventToTargetListeners(event: AWTKeyEvent) {
    keyListeners.forEach { listener ->
        if (!isPopupKeyListenerReady(listener)) return@forEach
        when (event.id) {
            AWTKeyEvent.KEY_PRESSED -> listener.keyPressed(event)
            AWTKeyEvent.KEY_RELEASED -> listener.keyReleased(event)
            AWTKeyEvent.KEY_TYPED -> listener.keyTyped(event)
        }
    }
}

private fun Component.findPopupKeyEventTargetOrNull(): Component? {
    if (keyListeners.any { isPopupKeyListenerReady(it) }) return this
    if (this is Container) {
        components.forEach { child ->
            val target = child.findPopupKeyEventTargetOrNull()
            if (target != null) return target
        }
    }
    return null
}

// preMeasureComposePanel creates ComposeContainer before the ComposePanel is attached to a window, which registers
// ComposeSceneMediator's KeyListener before ComposeContainer.addNotify() attaches BackNavigationEventInput to its
// NavigationEventDispatcher. Skip scene key listeners until the component is displayable.
private fun Component.isPopupKeyListenerReady(listener: java.awt.event.KeyListener): Boolean =
    isDisplayable || !listener.javaClass.name.startsWith("androidx.compose.ui.scene.")

internal fun isDismissRequestKey(type: KeyEventType, key: Key, hasModifier: Boolean = false): Boolean =
    type == KeyEventType.KeyDown && key == Key.Escape && !hasModifier

internal fun isModifiedDismissRequestKey(type: KeyEventType, key: Key, hasModifier: Boolean): Boolean =
    type == KeyEventType.KeyDown && key == Key.Escape && hasModifier

internal fun KeyEvent.hasDismissKeyModifier(): Boolean =
    isMetaPressed || isCtrlPressed || isAltPressed || isShiftPressed

internal fun KeyEvent.isDismissRequest(): Boolean =
    isDismissRequestKey(type = type, key = key, hasModifier = hasDismissKeyModifier())

internal fun KeyEvent.isModifiedDismissRequest(): Boolean =
    isModifiedDismissRequestKey(type = type, key = key, hasModifier = hasDismissKeyModifier())

private val AWTKeyEvent.keyLocationForCompose
    get() = if (keyLocation == KEY_LOCATION_UNKNOWN) KEY_LOCATION_STANDARD else keyLocation

@OptIn(InternalComposeUiApi::class)
internal fun AWTKeyEvent.toComposeKeyEvent(): KeyEvent =
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

/**
 * When inheriting from another context, we need to ensure that values already provided in the current context are not
 * overridden.
 */
@Composable
private fun ProvideValuesFromOtherContext(context: CompositionLocalContext, content: @Composable () -> Unit) {
    val existingContext = currentCompositionLocalContext
    CompositionLocalProvider(context) { CompositionLocalProvider(existingContext, content) }
}

// TODO(CMP-10981): Remove this reflection once ComposePanel supports pre-attachment content measurement.
// ComposePanel creates its container in addNotify() and defers initial composition until the panel is displayable,
// whereas JBPopup needs the measured content size and screen coordinates before it creates the popup window.
private object ComposePanelReflection {
    val composeContainerField: Field =
        ComposePanel::class.java.getDeclaredField("_composeContainer").apply { isAccessible = true }
    val composeContentField: Field =
        ComposePanel::class.java.getDeclaredField("_composeContent").apply { isAccessible = true }
    val savedStateField: Field = ComposePanel::class.java.getDeclaredField("savedState").apply { isAccessible = true }
    val createComposeContainerMethod: Method =
        ComposePanel::class.java.getDeclaredMethod("createComposeContainer").apply { isAccessible = true }
    val setWindowParentMethod: Method =
        ComposePanel::class.java.getDeclaredMethod("setWindowParent", Window::class.java).apply { isAccessible = true }

    private val composeContainerClass: Class<*> = Class.forName("androidx.compose.ui.scene.ComposeContainer")
    val updateLifecycleStateMethod: Method =
        composeContainerClass.getDeclaredMethod("updateLifecycleState").apply { isAccessible = true }
    val setRedispatchUnconsumedMouseWheelEventsMethod: Method =
        composeContainerClass
            .getDeclaredMethod("setRedispatchUnconsumedMouseWheelEvents", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
    val setShowLayoutBoundsMethod: Method =
        composeContainerClass.getDeclaredMethod("setShowLayoutBounds", Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
        }
    val containerSetContentMethod: Method =
        composeContainerClass.getDeclaredMethod("setContent", Function2::class.java).apply { isAccessible = true }
    val mediatorField: Field = composeContainerClass.getDeclaredField("mediator").apply { isAccessible = true }

    private val mediatorClass: Class<*> = Class.forName("androidx.compose.ui.scene.ComposeSceneMediator")
    val getSceneMethod: Method = mediatorClass.getDeclaredMethod("getScene").apply { isAccessible = true }
    val onComponentAttachedField: Field =
        mediatorClass.getDeclaredField("_onComponentAttached").apply { isAccessible = true }
    val measureContentMethod: Method =
        mediatorClass.declaredMethods.first { it.name.startsWith("measureContent-") }.apply { isAccessible = true }

    private val composeSceneClass: Class<*> = Class.forName("androidx.compose.ui.scene.ComposeScene")
    val setDensityMethod: Method =
        composeSceneClass.getDeclaredMethod("setDensity", Density::class.java).apply { isAccessible = true }

    val unconstrainedConstraintsValue: Long =
        Constraints::class.java.getDeclaredMethod("unbox-impl").invoke(Constraints()) as Long
}

/**
 * Initializes the popup [ComposePanel] container and measures its content before [AbstractPopup] creates the window.
 *
 * When [AbstractPopup] later attaches [composePanel], `ComposePanel.addNotify` reuses `_composeContainer` and keeps the
 * existing composition.
 */
@OptIn(InternalComposeUiApi::class)
internal fun preMeasureComposePanel(composePanel: ComposePanel, owner: Component): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    var createdContainer = false
    val rootComponent = generateSequence<Component>(composePanel) { it.parent }.last()
    val graphicsConfigurationDonor =
        if (composePanel.graphicsConfiguration == null && rootComponent !is Window) {
            owner.graphicsConfiguration?.let { configuration ->
                object : Container() {
                        override fun getGraphicsConfiguration(): GraphicsConfiguration = configuration
                    }
                    .apply { add(rootComponent) }
            }
        } else {
            null
        }
    return try {
        runCatching {
                var container = ComposePanelReflection.composeContainerField.get(composePanel)
                if (container == null) {
                    container = ComposePanelReflection.createComposeContainerMethod.invoke(composePanel)
                    createdContainer = true
                    ComposePanelReflection.composeContainerField.set(composePanel, container)
                    ComposePanelReflection.updateLifecycleStateMethod.invoke(container)
                    ComposePanelReflection.setRedispatchUnconsumedMouseWheelEventsMethod.invoke(
                        container,
                        composePanel.redispatchUnconsumedMouseWheelEvents,
                    )
                    ComposePanelReflection.setShowLayoutBoundsMethod.invoke(container, composePanel.showLayoutBounds)
                    val content = ComposePanelReflection.composeContentField.get(composePanel)
                    if (content != null) {
                        ComposePanelReflection.containerSetContentMethod.invoke(container, content)
                    }
                    ComposePanelReflection.savedStateField.set(composePanel, null)
                }

                val mediator = ComposePanelReflection.mediatorField.get(container)
                val scene = ComposePanelReflection.getSceneMethod.invoke(mediator)
                ComposePanelReflection.setDensityMethod.invoke(scene, Density(JBUIScale.sysScale(owner)))

                val onAttached = ComposePanelReflection.onComponentAttachedField.get(mediator) as? Function0<*>
                if (onAttached != null) {
                    onAttached.invoke()
                    ComposePanelReflection.onComponentAttachedField.set(mediator, null)
                }

                ComposePanelReflection.measureContentMethod.invoke(
                    mediator,
                    ComposePanelReflection.unconstrainedConstraintsValue,
                )
            }
            .onFailure { exception ->
                val cause = (exception as? InvocationTargetException)?.targetException ?: exception
                if (cause is CancellationException || cause is ControlFlowException) {
                    throw cause
                }
                if (createdContainer && !composePanel.isDisplayable) {
                    runCatching { composePanel.dispose() }
                    runCatching { ComposePanelReflection.composeContainerField.set(composePanel, null) }
                }
                Logger.getInstance(JBPopupRenderer::class.java)
                    .warn("Failed to pre-measure ComposePanel for JBPopup", cause)
            }
            .isSuccess
    } finally {
        graphicsConfigurationDonor?.remove(rootComponent)
    }
}

internal fun updateComposePanelWindowParent(composePanel: ComposePanel) {
    runCatching {
            val popupWindow = SwingUtilities.getWindowAncestor(composePanel)
            if (popupWindow != null) {
                ComposePanelReflection.setWindowParentMethod.invoke(composePanel, popupWindow)
            }
        }
        .onFailure { exception ->
            val cause = (exception as? InvocationTargetException)?.targetException ?: exception
            if (cause is CancellationException || cause is ControlFlowException) {
                throw cause
            }
            Logger.getInstance(JBPopupRenderer::class.java)
                .warn("Failed to update ComposePanel window parent for JBPopup", cause)
        }
}
