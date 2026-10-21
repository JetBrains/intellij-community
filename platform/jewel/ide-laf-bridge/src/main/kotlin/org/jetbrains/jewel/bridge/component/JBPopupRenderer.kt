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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.ui.component.PopupRenderer

private const val ATTACH_CONTENT_BEFORE_MEASURING = "AttachContentBeforeMeasuring"

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
    val compositionLocalContext = currentCompositionLocalContext

    val parentBoundsInRoot = remember { mutableStateOf<IntRect?>(null) }
    val popupRectangle = remember { mutableStateOf<Rectangle?>(null) }
    var lastAppliedRectangle by remember { mutableStateOf<Rectangle?>(null) }
    var isPopupShown by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf<Boolean?>(null) }
    var dismissedWith by remember { mutableStateOf(InputMode.Touch) }
    LaunchedEffect(isVisible) {
        if (isVisible == false) {
            currentOnDismissRequest?.invoke(dismissedWith)
        }
    }

    var useHostLayoutFallback by remember { mutableStateOf(GraphicsEnvironment.isHeadless()) }
    val currentParentBounds = parentBoundsInRoot.value
    Layout(
        content = {
            if (useHostLayoutFallback && currentParentBounds != null) {
                ProvideValuesFromOtherContext(compositionLocalContext) { currentContent.value() }
            }
        },
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
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

    val (jewelComposePanelWrapper, popup) =
        remember {
            lateinit var wrapper: JewelComposePanelWrapper
            lateinit var jbPopup: JBPopup
            wrapper =
                compose {
                    val parentBounds = parentBoundsInRoot.value ?: return@compose

                    Layout(
                        modifier = Modifier.semantics { popup() },
                        content = { ProvideValuesFromOtherContext(compositionLocalContext) { currentContent.value() } },
                        measurePolicy =
                            remember(currentPopupPositionProvider, parentBounds) {
                                JBPopupMeasurePolicy(
                                    popupPositionProvider = currentPopupPositionProvider,
                                    screenSize = IntSize.fromScreenSize(owner),
                                    parentBoundsInWindow = parentBounds,
                                    onMeasure = { position, size ->
                                        val rectangle = Rectangle(position.x, position.y, size.width, size.height)
                                        popupRectangle.value = rectangle
                                        if (!jbPopup.isVisible && !jbPopup.isDisposed && owner.isShowing) {
                                            applyPopupBounds(jbPopup, wrapper, owner, rectangle)
                                            lastAppliedRectangle = rectangle
                                        }
                                    },
                                )
                            },
                    )
                }
                    as JewelComposePanelWrapper
            wrapper.putClientProperty(ATTACH_CONTENT_BEFORE_MEASURING, true)
            jbPopup =
                JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(wrapper, wrapper.composePanel)
                    .setFocusable(currentProperties.focusable)
                    .setRequestFocus(currentProperties.focusable)
                    .setCancelOnClickOutside(currentProperties.dismissOnClickOutside)
                    .setCancelOnWindowDeactivation(currentProperties.dismissOnClickOutside)
                    .setLocateWithinScreenBounds(false)
                    // AbstractPopup cancels itself on Escape whenever the handler below declines the event, which would
                    // close the popup behind Compose's back and leave the two out of sync. Take sole ownership instead.
                    .setCancelKeyEnabled(false)
                    .setKeyEventHandler { event ->
                        val composeEvent = event.toComposeKeyEvent()
                        val consumed =
                            currentOnPreviewKeyEvent?.invoke(composeEvent) == true ||
                                currentOnKeyEvent?.invoke(composeEvent) == true
                        // Whether Escape dismisses is the popup's declared policy, not something inferred from the
                        // shape of its callbacks. A null callback means we have no way to dismiss, so we must not
                        // claim the key.
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
                    .addListener(
                        object : JBPopupListener {
                            override fun beforeShown(event: LightweightWindowEvent) {
                                isVisible = true
                            }

                            override fun onClosed(event: LightweightWindowEvent) {
                                isVisible = false
                            }
                        }
                    )
                    .createPopup()
            wrapper to jbPopup
        }

    // The builder above only captures the properties as they were on first composition. AbstractPopup reads these
    // flags live on every event, so pushing changes keeps a popup whose properties vary (a ComboBox suppressing
    // click-outside dismissal while its chevron is hovered) in step without recreating the native window.
    LaunchedEffect(popup, currentProperties) {
        val abstractPopup = popup as? AbstractPopup ?: return@LaunchedEffect
        abstractPopup.setCancelOnClickOutside(currentProperties.dismissOnClickOutside)
        abstractPopup.setCancelOnWindowDeactivation(currentProperties.dismissOnClickOutside)
    }

    val rectValue = popupRectangle.value
    LaunchedEffect(currentParentBounds, rectValue, useHostLayoutFallback) {
        if (popup.isDisposed || !owner.isShowing) return@LaunchedEffect
        if (!useHostLayoutFallback && !isPopupShown && currentParentBounds != null) {
            isPopupShown = true
            popup.showInScreenCoordinates(owner, owner.locationOnScreen)
            if (popupRectangle.value == null) {
                isPopupShown = false
                useHostLayoutFallback = true
                return@LaunchedEffect
            }
        }
        val rectangle = popupRectangle.value ?: return@LaunchedEffect
        if (rectangle != lastAppliedRectangle) {
            applyPopupBounds(popup, jewelComposePanelWrapper, owner, rectangle)
            lastAppliedRectangle = rectangle
        }
        if (!isPopupShown) {
            isPopupShown = true
            popup.showInScreenCoordinates(owner, rectangle.location.fromCurrentScreenToGlobal(owner))
        }
    }

    DisposableEffect(popup) {
        onDispose {
            popup.cancel()
            if (!jewelComposePanelWrapper.composePanel.isDisplayable) {
                jewelComposePanelWrapper.composePanel.dispose()
            }
        }
    }
}

private fun applyPopupBounds(
    popup: JBPopup,
    wrapper: JewelComposePanelWrapper,
    owner: Component,
    rectangle: Rectangle,
) {
    val globalLocation = rectangle.location.fromCurrentScreenToGlobal(owner)
    val globalSize = rectangle.size.withSystemDensity(owner)
    wrapper.preferredSize = globalSize
    wrapper.composePanel.preferredSize = globalSize
    popup.setSize(globalLocation, globalSize)
}

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

private fun KeyEvent.isDismissRequest() = type == KeyEventType.KeyDown && key == Key.Escape

private val java.awt.event.KeyEvent.keyLocationForCompose
    get() = if (keyLocation == KEY_LOCATION_UNKNOWN) KEY_LOCATION_STANDARD else keyLocation

@OptIn(InternalComposeUiApi::class)
private fun java.awt.event.KeyEvent.toComposeKeyEvent(): KeyEvent =
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
