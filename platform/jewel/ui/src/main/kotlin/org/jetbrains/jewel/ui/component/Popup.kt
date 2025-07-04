package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup as ComposePopup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.JewelConfigs
import org.jetbrains.jewel.ui.component.PopupRender.PopupData

/**
 * Displays a popup with the provided content at a position determined by the given [PopupPositionProvider].
 *
 * This function behavior is influenced by the 'jewel.customPopupRender' system property. 
 * If set to `true`, it allows using a custom popup rendering implementation; otherwise, it defaults to the standard Compose popup.
 * 
 * If running on the IntelliJ Platform and setting the 'jewel.customPopupRender' property to `true`, the plugin will use the JBPopup
 * implementation for rendering popups. This is useful if your composable content is small and you need to display a popup that is
 * bigger than the component size.
 *
 * @param popupPositionProvider Determines the position of the popup on the screen.
 * @param onDismissRequest Callback invoked when a dismiss event is requested, typically when the popup is dismissed.
 * @param properties Configuration parameters for the popup, such as whether it should consume touch events or focusable behavior.
 * @param onPreviewKeyEvent Callback invoked for key events before they are dispatched to children. Return `true` to consume the event.
 * @param onKeyEvent Callback invoked for key events after they are dispatched to children. Return `true` to consume the event.
 * @param content The composable content to be displayed inside the popup.
 */
@Composable
@InternalJewelApi
public fun Popup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val popupRender = if (JewelConfigs.useCustomPopupRender) LocalPopupRender.current else DefaultPopupRender
    popupRender.Popup(
        arguments =
            PopupData(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = onDismissRequest,
                properties = properties,
                onPreviewKeyEvent = onPreviewKeyEvent,
                onKeyEvent = onKeyEvent,
            ),
        content = content,
    )
}

public interface PopupRender {
    @Composable public fun Popup(arguments: PopupData, content: @Composable () -> Unit)

    public class PopupData(
        public val popupPositionProvider: PopupPositionProvider,
        public val onDismissRequest: (() -> Unit)? = null,
        public val properties: PopupProperties = PopupProperties(),
        public val onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
        public val onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    )

    public companion object
}

@InternalJewelApi
public val LocalPopupRender: ProvidableCompositionLocal<PopupRender> = staticCompositionLocalOf { DefaultPopupRender }

private object DefaultPopupRender : PopupRender {
    @Composable
    override fun Popup(arguments: PopupData, content: @Composable () -> Unit) {
        with(arguments) {
            ComposePopup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = onDismissRequest,
                properties = properties,
                onPreviewKeyEvent = onPreviewKeyEvent,
                onKeyEvent = onKeyEvent,
                content = content,
            )
        }
    }
}

internal fun handlePopupMenuOnKeyEvent(
    keyEvent: KeyEvent,
    focusManager: FocusManager,
    inputModeManager: InputModeManager,
    menuManager: MenuManager,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    return when (keyEvent.key) {
        Key.DirectionDown -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusManager.moveFocus(FocusDirection.Next)
            true
        }

        Key.DirectionUp -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusManager.moveFocus(FocusDirection.Previous)
            true
        }

        Key.Escape -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            menuManager.closeAll(InputMode.Keyboard, true)
            true
        }

        Key.DirectionLeft -> {
            if (menuManager.isSubmenu()) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                menuManager.close(InputMode.Keyboard)
                true
            } else {
                false
            }
        }

        else -> false
    }
}

@Immutable
internal data class AnchorVerticalMenuPositionProvider(
    val contentOffset: DpOffset,
    val contentMargin: PaddingValues,
    val alignment: Alignment.Horizontal,
    val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val topMargin = with(density) { contentMargin.calculateTopPadding().roundToPx() }
        val bottomMargin = with(density) { contentMargin.calculateBottomPadding().roundToPx() }
        val leftMargin = with(density) { contentMargin.calculateLeftPadding(layoutDirection).roundToPx() }
        val rightMargin = with(density) { contentMargin.calculateRightPadding(layoutDirection).roundToPx() }

        val windowSpaceBounds =
            IntRect(
                left = leftMargin,
                top = topMargin,
                right = windowSize.width - rightMargin,
                bottom = windowSize.height - bottomMargin,
            )

        // The content offset specified using the dropdown offset parameter.
        val contentOffsetX =
            with(density) { contentOffset.x.roundToPx() * if (layoutDirection == LayoutDirection.Ltr) 1 else -1 }
        val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

        // Compute horizontal position.
        val x =
            anchorBounds.left +
                alignment.align(popupContentSize.width, anchorBounds.width, layoutDirection) +
                contentOffsetX

        // Compute vertical position.
        val aboveSpacing = anchorBounds.top - contentOffsetY - topMargin
        val belowSpacing = windowSize.height - anchorBounds.bottom - contentOffsetY - bottomMargin

        // When the space below is large enough,
        // or the space below is larger than the space above,
        // the dropdown menu is displayed below by default.
        val y =
            if (belowSpacing > popupContentSize.height || belowSpacing >= aboveSpacing) {
                anchorBounds.bottom + contentOffsetY
            } else {
                anchorBounds.top - contentOffsetY - popupContentSize.height
            }

        val popupBounds =
            IntRect(x, y, x + popupContentSize.width, y + popupContentSize.height).constrainedIn(windowSpaceBounds)

        return IntOffset(popupBounds.left, popupBounds.top)
    }
}

@Immutable
internal data class AnchorHorizontalMenuPositionProvider(
    val contentOffset: DpOffset,
    val contentMargin: PaddingValues,
    val alignment: Alignment.Vertical,
    val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val topMargin = with(density) { contentMargin.calculateTopPadding().roundToPx() }
        val bottomMargin = with(density) { contentMargin.calculateBottomPadding().roundToPx() }
        val leftMargin = with(density) { contentMargin.calculateLeftPadding(layoutDirection).roundToPx() }
        val rightMargin = with(density) { contentMargin.calculateRightPadding(layoutDirection).roundToPx() }

        val windowSpaceBounds =
            IntRect(
                left = leftMargin,
                top = topMargin,
                right = windowSize.width - rightMargin,
                bottom = windowSize.height - bottomMargin,
            )

        // The content offset specified using the dropdown offset parameter.
        val contentOffsetX =
            with(density) { contentOffset.x.roundToPx() * if (layoutDirection == LayoutDirection.Ltr) 1 else -1 }
        val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

        // Compute horizontal position.
        val y = anchorBounds.top + alignment.align(popupContentSize.height, anchorBounds.height) + contentOffsetY

        // Compute vertical position.
        val leftSpacing = anchorBounds.left - contentOffsetX - windowSpaceBounds.left
        val rightSpacing = windowSpaceBounds.width - anchorBounds.right - contentOffsetY

        val x =
            if (rightSpacing > popupContentSize.width || rightSpacing >= leftSpacing) {
                anchorBounds.right + contentOffsetX
            } else {
                anchorBounds.left - contentOffsetX - popupContentSize.width
            }

        val popupBounds =
            IntRect(x, y, x + popupContentSize.width, y + popupContentSize.height).constrainedIn(windowSpaceBounds)

        return IntOffset(popupBounds.left, popupBounds.top)
    }
}

internal fun IntRect.constrainedIn(rect: IntRect): IntRect {
    var x = left
    if (right > rect.right) {
        x = rect.right - width
    }
    if (x < rect.left) {
        x = rect.left
    }

    var y = top
    if (bottom > rect.bottom) {
        y = rect.bottom - height
    }
    if (y < rect.top) {
        y = rect.top
    }

    return IntRect(x, y, x + width, y + height)
}
