// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.foundation.focusable
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
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Popup as ComposePopup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jetbrains.JBR
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.floor
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.PopupRenderer

/**
 * A popup renderer implementation that uses [JDialog] to display popups in a Compose UI.
 *
 * This renderer creates a [JDialog] that hosts Compose content, allowing for integration between Swing and Compose UI
 * hierarchies.
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
    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        cornerSize: CornerSize,
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

        if (!isJBREnvironment && !supportBlending || window == null) {
            ComposePopup(
                popupPositionProvider = popupPositionProvider,
                properties = properties,
                onDismissRequest = onDismissRequest,
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
                content = content,
            )
        }
    }
}

@Composable
private fun JPopupImpl(
    window: Window,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
    onKeyEvent: ((KeyEvent) -> Boolean)?,
    cornerSize: CornerSize,
    blendingEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val popupDensity = LocalDensity.current
    val component = LocalComponent.current

    val currentContent by rememberUpdatedState(content)
    val currentPopupPositionProvider by rememberUpdatedState(popupPositionProvider)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)

    val compositionLocalContext by rememberUpdatedState(currentCompositionLocalContext)

    var parentBoundsInRoot by remember { mutableStateOf<IntRect?>(null) }
    var popupRectangle by remember { mutableStateOf<Rectangle?>(null) }

    Layout(
        content = {},
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
                childCoordinates.parentCoordinates?.let {
                    parentBoundsInRoot = it.boundsInRoot().roundToIntRect().fromRelativeToScreen(component)
                }
            },
        measurePolicy = { _, _ -> layout(0, 0) {} },
    )

    val overriddenOnKeyEvent =
        if (properties.dismissOnBackPress && onDismissRequest != null) {
            // No need to remember this lambda, as it doesn't capture any values that can change.
            { event: KeyEvent ->
                val consumed = currentOnKeyEvent?.invoke(event) ?: false
                if (!consumed && event.isDismissRequest()) {
                    currentOnDismissRequest?.invoke()
                    true
                } else {
                    consumed
                }
            }
        } else {
            onKeyEvent
        }

    val dialog = remember {
        JDialog(window).apply {
            isAlwaysOnTop = true
            isUndecorated = true
            rootPane.isOpaque = false
            background = Color(0, 0, 0, 0)
            contentPane.background = Color(0, 0, 0, 0)
            rootPane.putClientProperty("Window.shadow", true)
        }
    }

    val composePanel = remember {
        ComposePanel().apply {
            layout = null
            isOpaque = false
            background = Color(0, 0, 0, 0)

            setContent {
                ProvideValuesFromOtherContext(compositionLocalContext) {
                    val positionProvider = currentPopupPositionProvider
                    val parentBounds = parentBoundsInRoot ?: return@ProvideValuesFromOtherContext

                    Layout(
                        content = {
                            CompositionLocalProvider(LocalComponent provides this@apply) {
                                currentContent()

                                val focusManager = LocalFocusManager.current
                                LaunchedEffect(Unit) {
                                    if (properties.focusable) {
                                        focusManager.moveFocus(FocusDirection.Enter)
                                    }
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
                                JPopupMeasurePolicy(dialog, popupPositionProvider, parentBounds) { position, size ->
                                    popupRectangle = Rectangle(position.x, position.y, size.width, size.height)

                                    if (blendingEnabled) {
                                        // If any of the blending logic is enabled, we don't need to use JBR APIs
                                        // to set the rounded corners and fix the background.
                                        return@JPopupMeasurePolicy
                                    }

                                    if (cornerSize != ZeroCornerSize) {
                                        JBR.getRoundedCornersManager()
                                            .setRoundedCorners(
                                                dialog,
                                                cornerSize.toPx(size.toSize(), popupDensity) / dialog.density(),
                                            )
                                    }
                                }
                            },
                    )
                }
            }
        }
    }

    LaunchedEffect(properties) {
        dialog.isFocusable = properties.focusable
        dialog.focusableWindowState = properties.focusable
    }

    val rectValue = popupRectangle
    LaunchedEffect(rectValue) {
        val rectangle = rectValue?.withDensity(popupDensity.density) ?: return@LaunchedEffect
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
                            currentOnDismissRequest?.invoke()
                            event.consume()
                        }
                    }
                }
                is WindowEvent -> {
                    if (event.id == WindowEvent.WINDOW_LOST_FOCUS && event.window == dialog) {
                        currentOnDismissRequest?.invoke()
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
            composePanel.dispose()
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

/**
 * When inheriting from another context, we need to ensure that values already provided in the current context are not
 * overridden.
 */
@Composable
private fun ProvideValuesFromOtherContext(context: CompositionLocalContext, content: @Composable () -> Unit) {
    val existingContext = currentCompositionLocalContext
    CompositionLocalProvider(context) { CompositionLocalProvider(existingContext, content) }
}

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

private fun KeyEvent.isDismissRequest() = type == KeyEventType.KeyDown && key == Key.Escape

private val <T> CompositionLocal<T>.currentOrNull
    @Composable get() = runCatching { current }.getOrNull()
