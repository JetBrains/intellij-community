package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.window.PopupPositionProvider

internal fun handlePopupMenuOnKeyEvent(
    keyEvent: KeyEvent,
    focusManager: FocusManager,
    inputModeManager: InputModeManager,
    menuController: MenuController,
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
            menuController.closeAll(InputMode.Keyboard, true)
            true
        }

        Key.DirectionLeft -> {
            if (menuController.isSubmenu()) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                menuController.close(InputMode.Keyboard)
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
