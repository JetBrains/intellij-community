package org.jetbrains.jewel.intui.standalone.component

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.DefaultPopupRenderer
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.component.PopupRenderer

@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideNativeWindowPopupRenderer(window: Window, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPopupRenderer provides JDialogRenderer(window)) { content() }
}

/**
 * A popup renderer implementation that uses JDialog to display popups in a Compose UI.
 *
 * This renderer creates a JDialog that hosts Compose content, allowing for integration between Swing and Compose UI
 * hierarchies.
 *
 * Note that this renderer requires you to enable the 'compose.interop.blending' system property. Otherwise, the popup
 * will be rendered with a white background. For more information, check the official documentation available at
 * https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-swing-interoperability.html#experimental-interop-blending
 *
 * @param window The parent window that will own the popup dialog
 */
internal class JDialogRenderer(private val window: Window) : PopupRenderer {
    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        content: @Composable (() -> Unit),
    ) {
        if (!JewelFlags.isSwingBlendEnabled) {
            LaunchedEffect(Unit) {
                JewelLogger.getInstance("PopupRenderer")
                    .warn(
                        "JDialogRenderer is used without enabling the 'compose.interop.blending' system property. " +
                            "Fall-backing to the default popup renderer."
                    )
            }

            DefaultPopupRenderer.Popup(
                popupPositionProvider = popupPositionProvider,
                properties = properties,
                onDismissRequest = onDismissRequest,
                onPreviewKeyEvent = onPreviewKeyEvent,
                onKeyEvent = onKeyEvent,
                content = content,
            )

            return
        }

        JPopupImpl(
            window = window,
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
private fun JPopupImpl(
    window: Window,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val currentPopupPositionProvider = rememberUpdatedState(popupPositionProvider)

    val compositionLocalContext = currentCompositionLocalContext

    val parentBoundsInRoot = remember { mutableStateOf<IntRect?>(null) }
    val popupRectangle = remember { mutableStateOf<Rectangle?>(null) }

    Layout(
        content = {},
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
                childCoordinates.parentCoordinates?.let {
                    parentBoundsInRoot.value = it.boundsInRoot().roundToIntRect().fromRelativeToScreen(window)
                }
            },
        measurePolicy = { _, _ -> layout(0, 0) {} },
    )

    val currentOnDismissRequest = rememberUpdatedState(onDismissRequest)
    val currentOnKeyEvent = rememberUpdatedState(onKeyEvent)

    val overriddenOnKeyEvent =
        if (properties.dismissOnBackPress && onDismissRequest != null) {
            // No need to remember this lambda, as it doesn't capture any values that can change.
            { event: KeyEvent ->
                val consumed = currentOnKeyEvent.value?.invoke(event) ?: false
                if (!consumed && event.isDismissRequest()) {
                    currentOnDismissRequest.value?.invoke()
                    true
                } else {
                    consumed
                }
            }
        } else {
            onKeyEvent
        }

    val composePanel = remember {
        ComposePanel().apply {
            layout = null
            isOpaque = false
            background = java.awt.Color(0, 0, 0, 0)

            setContent {
                ProvideValuesFromOtherContext(compositionLocalContext) {
                    val positionProvider = currentPopupPositionProvider.value
                    val parentBounds = parentBoundsInRoot.value ?: return@ProvideValuesFromOtherContext

                    Layout(
                        content = {
                            currentContent.value()

                            val focusManager = LocalFocusManager.current
                            LaunchedEffect(Unit) {
                                if (properties.focusable) {
                                    focusManager.moveFocus(FocusDirection.Enter)
                                }
                            }
                        },
                        modifier =
                            Modifier.focusable(properties.focusable)
                                .semantics { popup() }
                                .thenIf(onPreviewKeyEvent != null) {
                                    Modifier.onPreviewKeyEvent(onPreviewKeyEvent ?: { false })
                                }
                                .thenIf(overriddenOnKeyEvent != null) {
                                    Modifier.onKeyEvent(overriddenOnKeyEvent ?: { false })
                                },
                        measurePolicy =
                            remember(positionProvider, parentBounds) {
                                JPopupMeasurePolicy(window, popupPositionProvider, parentBounds) { position, size ->
                                    popupRectangle.value = Rectangle(position.x, position.y, size.width, size.height)
                                }
                            },
                    )
                }
            }
        }
    }

    val dialog = remember {
        JDialog(window).apply {
            isAlwaysOnTop = true
            isFocusable = properties.focusable
            focusableWindowState = properties.focusable
            isUndecorated = true
            rootPane.isOpaque = false
            background = java.awt.Color(0, 0, 0, 0)
            contentPane.background = java.awt.Color(0, 0, 0, 0)
            rootPane.putClientProperty("Window.shadow", true)
        }
    }

    val rectValue = popupRectangle.value
    val density = LocalDensity.current.density
    LaunchedEffect(rectValue) {
        val rectangle = rectValue?.withDensity(density) ?: return@LaunchedEffect
        dialog.size = rectangle.size
        dialog.location = rectangle.location.fromCurrentScreenToGlobal(window)
    }

    DisposableEffect(composePanel) {
        val listener = AWTEventListener { event ->
            when (event) {
                is MouseEvent -> {
                    if (event.button != MouseEvent.NOBUTTON) {
                        val mousePosition = event.locationOnScreen
                        if (!dialog.bounds.contains(mousePosition)) {
                            currentOnDismissRequest.value?.invoke()
                            event.consume()
                        }
                    }
                }
                is WindowEvent -> {
                    if (event.id == WindowEvent.WINDOW_LOST_FOCUS && event.window == dialog) {
                        currentOnDismissRequest.value?.invoke()
                    }
                }
            }
        }

        Toolkit.getDefaultToolkit()
            .addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK)
        onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    DisposableEffect(dialog) {
        dialog.contentPane = composePanel

        dialog.isVisible = true
        dialog.size = composePanel.preferredSize

        onDispose {
            dialog.isVisible = false
            dialog.dispose()
        }
    }
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
}

// Based on implementation from JBUIScale and ScreenUtil
private fun IntSize.Companion.screenSize(window: Window): IntSize {
    val windowConfiguration = window.graphicsConfiguration.device.defaultConfiguration

    val screenSize = windowConfiguration.bounds
    val scale = windowConfiguration.defaultTransform.scaleX

    return IntSize((screenSize.width * scale).fastRoundToInt(), (screenSize.height * scale).fastRoundToInt())
}

private fun Rectangle.withDensity(density: Float): Rectangle =
    Rectangle(
        (x / density).fastRoundToInt(),
        (y / density).fastRoundToInt(),
        (width / density).fastRoundToInt(),
        (height / density).fastRoundToInt(),
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
private fun IntRect.fromRelativeToScreen(window: Window): IntRect {
    val windowConfiguration = window.graphicsConfiguration.device.defaultConfiguration

    val density = windowConfiguration.defaultTransform.scaleX
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
private fun Window.locationOnDisplay(): Point {
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

private fun KeyEvent.isDismissRequest() = type == KeyEventType.KeyDown && key == Key.Escape
