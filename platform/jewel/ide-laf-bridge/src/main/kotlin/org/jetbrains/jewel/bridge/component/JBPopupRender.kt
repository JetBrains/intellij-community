package org.jetbrains.jewel.bridge.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.PopupPositionProvider
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.ui.component.PopupRender
import org.jetbrains.jewel.ui.component.PopupRender.Arguments

internal object JBPopupRender : PopupRender {
    @Composable
    override fun Popup(arguments: Arguments, content: @Composable () -> Unit) {
        JBPopupLayout(arguments, content)
    }
}

@Composable
private fun JBPopupLayout(arguments: Arguments, content: @Composable () -> Unit) {
    val currentContent = rememberUpdatedState(content)
    val currentArguments = rememberUpdatedState(arguments)

    val owner = LocalComponent.current
    val density = LocalDensity.current.density

    val compositionLocalContext = currentCompositionLocalContext

    val layoutParentBoundsInWindow = remember<MutableState<IntRect?>> { mutableStateOf(null) }
    val popupRectangle = remember { mutableStateOf<Rectangle?>(null) }

    Layout(
        content = {},
        modifier =
            Modifier.onGloballyPositioned { childCoordinates ->
                childCoordinates.parentCoordinates?.let {
                    val layoutPosition = it.positionInWindow().round()
                    val layoutSize = it.size
                    layoutParentBoundsInWindow.value = IntRect(layoutPosition, layoutSize)
                }
            },
        measurePolicy = { _, _ -> layout(0, 0) {} },
    )

    val popup: JBPopup = remember {
        val composePanel =
            ComposePanel().apply {
                // Setting initial dimensions as 1x1 to calculate the size
                preferredSize = Dimension(1, 1)

                // Setting content and inheriting the composition local context
                setContent {
                    CompositionLocalProvider(compositionLocalContext) {
                        CompositionLocalProvider(LocalComponent provides this) {
                            val density = LocalDensity.current.density

                            val arguments = currentArguments.value
                            val parentBounds = layoutParentBoundsInWindow.value ?: return@CompositionLocalProvider

                            Layout(
                                modifier =
                                    Modifier.semantics { popup() }
                                        .thenIf(popupRectangle.value != null) { fillMaxSize() },
                                content = { currentContent.value() },
                                measurePolicy =
                                    remember(this, arguments, parentBounds) {
                                        JBPopupMeasurePolicy(
                                            popupPositionProvider = arguments.popupPositionProvider,
                                            containerSize = ScreenUtil.getScreenRectangle(this),
                                            parentBoundsInWindow = parentBounds,
                                            onMeasure = { position, size ->
                                                popupRectangle.value =
                                                    Rectangle(
                                                        (position.x / density).fastRoundToInt(),
                                                        (position.y / density).fastRoundToInt(),
                                                        (size.width / density).fastRoundToInt(),
                                                        (size.height / density).fastRoundToInt(),
                                                    )
                                            },
                                        )
                                    },
                            )
                        }
                    }
                }
            }

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(composePanel, null)
            .setFocusable(arguments.properties.focusable)
            .setRequestFocus(arguments.properties.focusable)
            .setKeyEventHandler { arguments.onKeyEvent?.invoke(KeyEvent(it)) ?: false }
            .setCancelOnClickOutside(arguments.properties.dismissOnClickOutside)
            .setCancelOnWindowDeactivation(true)
            .setLocateWithinScreenBounds(false)
            .setCancelCallback {
                arguments.onDismissRequest?.invoke()
                true
            }
            .createPopup()
    }

    LaunchedEffect(Unit) {
        val bounds = layoutParentBoundsInWindow.value
        if (bounds == null) {
            return@LaunchedEffect
        }

        popup.show(
            RelativePoint(
                owner,
                Point((bounds.left / density).fastRoundToInt(), (bounds.bottom / density).fastRoundToInt()),
            )
        )
    }

    val rectValue = popupRectangle.value
    LaunchedEffect(rectValue) {
        val rectangle = rectValue ?: return@LaunchedEffect

        popup.setSize(RelativePoint(owner, rectangle.location).screenPoint, rectangle.size)
    }

    DisposableEffect(Unit) { onDispose { popup.cancel() } }
}

private class JBPopupMeasurePolicy(
    private val popupPositionProvider: PopupPositionProvider,
    private val containerSize: Rectangle,
    private val parentBoundsInWindow: IntRect,
    private val onMeasure: (IntOffset, IntSize) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val relaxedConstraints =
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = containerSize.width,
                maxHeight = containerSize.height,
            )

        val placeables = measurables.fastMap { it.measure(relaxedConstraints) }
        val contentSize =
            IntSize(
                width = placeables.fastMaxBy { it.width }?.width ?: constraints.minWidth,
                height = placeables.fastMaxBy { it.height }?.height ?: constraints.minHeight,
            )

        val position =
            popupPositionProvider.calculatePosition(
                anchorBounds = parentBoundsInWindow,
                windowSize = IntSize(containerSize.width, containerSize.height),
                layoutDirection = layoutDirection,
                popupContentSize = contentSize,
            )

        onMeasure(position, contentSize)

        return layout(constraints.maxWidth, constraints.maxHeight) { placeables.fastForEach { it.place(0, 0) } }
    }
}
