package org.jetbrains.jewel.bridge.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.PopupRenderer

internal object JBPopupRenderer : PopupRenderer {
    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        content: @Composable () -> Unit,
    ) {
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
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val currentPopupPositionProvider = rememberUpdatedState(popupPositionProvider)

    val owner = LocalComponent.current
    val compositionLocalContext = currentCompositionLocalContext

    val parentBoundsInRoot = remember { mutableStateOf<IntRect?>(null) }
    val popupRectangle = remember { mutableStateOf<Rectangle?>(null) }

    Layout(
        content = {},
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
                childCoordinates.parentCoordinates?.let {
                    parentBoundsInRoot.value = it.boundsInRoot().roundToIntRect().fromRelativeToScreen(owner)
                }
            },
        measurePolicy = { _, _ -> layout(0, 0) {} },
    )

    val popup: JBPopup = remember {
        val jewelComposePanelWrapper =
            compose(
                config = {
                    // Setting initial dimensions as 1x1 to calculate the size
                    preferredSize = Dimension(1, 1)
                },
                content = {
                    ProvideValuesFromOtherContext(compositionLocalContext) {
                        val focusRequester = remember { FocusRequester() }
                        val positionProvider = currentPopupPositionProvider.value
                        val parentBounds = parentBoundsInRoot.value ?: return@ProvideValuesFromOtherContext

                        Layout(
                            modifier = Modifier.focusRequester(focusRequester).semantics { popup() },
                            content = {
                                currentContent.value()

                                LaunchedEffect(Unit) {
                                    if (properties.focusable) {
                                        focusRequester.requestFocus()
                                    }
                                }
                            },
                            measurePolicy =
                                remember(positionProvider, parentBounds) {
                                    JBPopupMeasurePolicy(
                                        popupPositionProvider = positionProvider,
                                        screenSize = IntSize.fromScreenSize(owner),
                                        parentBoundsInWindow = parentBounds,
                                        onMeasure = { position, size ->
                                            popupRectangle.value =
                                                Rectangle(position.x, position.y, size.width, size.height)
                                        },
                                    )
                                },
                        )
                    }
                },
            )
                as JewelComposePanelWrapper

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(jewelComposePanelWrapper, jewelComposePanelWrapper.composePanel)
            .setFocusable(properties.focusable)
            .setRequestFocus(properties.focusable)
            .setCancelOnClickOutside(properties.dismissOnClickOutside)
            .setCancelOnWindowDeactivation(true)
            .setLocateWithinScreenBounds(false)
            .setKeyEventHandler { event ->
                val composeEvent = event.toComposeKeyEvent()
                onPreviewKeyEvent?.invoke(composeEvent) == true || onKeyEvent?.invoke(composeEvent) == true
            }
            .setCancelCallback {
                onDismissRequest?.invoke()
                true
            }
            .createPopup()
    }

    val rectValue = popupRectangle.value
    LaunchedEffect(rectValue) {
        val rectangle = rectValue ?: return@LaunchedEffect
        popup.setSize(rectangle.location.fromCurrentScreenToGlobal(owner), rectangle.size.withSystemDensity(owner))
    }

    DisposableEffect(Unit) {
        // Showing in the top-left corner of the owner component so we can measure and show it correctly
        popup.showInScreenCoordinates(owner, Point(0, 0))
        onDispose { popup.cancel() }
    }
}

private class JBPopupMeasurePolicy(
    private val popupPositionProvider: PopupPositionProvider,
    private val screenSize: IntSize,
    private val parentBoundsInWindow: IntRect,
    private val onMeasure: (IntOffset, IntSize) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
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

        return layout(constraints.maxWidth, constraints.maxHeight) { placeables.fastForEach { it.place(0, 0) } }
    }
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
