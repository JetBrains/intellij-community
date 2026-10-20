// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

internal object PopupEventRouting {
    fun shouldDispatchKeyEventToPopup(
        popupFocusable: Boolean,
        popupVisible: Boolean,
        popupActive: Boolean,
        ownerActive: Boolean,
    ): Boolean = popupFocusable && popupVisible && (popupActive || ownerActive)

    fun shouldDismissOnMousePress(
        popupFocusable: Boolean,
        popupActive: Boolean,
        eventInPopupOrOwnedChild: Boolean,
        eventInsidePopupBounds: Boolean,
        eventComponentInOwnerWindow: Boolean,
    ): Boolean =
        (!popupFocusable || (!popupActive && !eventComponentInOwnerWindow)) &&
            !eventInPopupOrOwnedChild &&
            !eventInsidePopupBounds

    fun shouldDismissOnFocusLoss(
        dismissOnClickOutside: Boolean,
        popupFocusable: Boolean,
        isFocusLostEvent: Boolean,
        focusLostWindowIsPopup: Boolean,
        oppositeWindowInPopupOrOwnedChild: Boolean,
        oppositeWindowIsPopupOwner: Boolean,
    ): Boolean =
        dismissOnClickOutside &&
            popupFocusable &&
            isFocusLostEvent &&
            focusLostWindowIsPopup &&
            !oppositeWindowInPopupOrOwnedChild &&
            !oppositeWindowIsPopupOwner

    fun shouldConsumeOwnerActiveKeyAfterRedispatch(
        redispatchedEventConsumed: Boolean,
        isDismissRequest: Boolean,
        isModifiedDismissRequest: Boolean,
    ): Boolean = redispatchedEventConsumed || isDismissRequest || !isModifiedDismissRequest

    /**
     * Returns whether a `MOUSE_RELEASED` event in the owner window should defer popup dismissal to the next EDT turn.
     *
     * Focusable popups do not dismiss on `WINDOW_LOST_FOCUS` when focus returns to the owner window. Instead, the
     * renderer defers dismissal until `MOUSE_RELEASED` via `SwingUtilities.invokeLater`. This order lets Compose finish
     * the click gesture on a toggle button before the dismissal callback runs.
     *
     * The popup must observe the matching outside press while the popup is already open. A context menu that opens on a
     * secondary-button press does not dismiss when the matching release arrives after the popup opens.
     */
    fun shouldDeferDismissOnMouseReleased(
        popupFocusable: Boolean,
        dismissOnClickOutside: Boolean,
        isMouseReleasedEvent: Boolean,
        matchingOwnerOutsidePressObserved: Boolean,
        eventComponentIsPopupDescendant: Boolean,
        eventInsidePopupBounds: Boolean,
        eventComponentInOwnerWindow: Boolean,
    ): Boolean =
        popupFocusable &&
            dismissOnClickOutside &&
            isMouseReleasedEvent &&
            matchingOwnerOutsidePressObserved &&
            !eventComponentIsPopupDescendant &&
            !eventInsidePopupBounds &&
            eventComponentInOwnerWindow
}

internal class PopupOutsidePressTracker {
    private val pendingButtons = mutableSetOf<Int>()

    fun onPress(button: Int, isOwnerOutsidePress: Boolean) {
        if (isOwnerOutsidePress) {
            pendingButtons.add(button)
        } else {
            pendingButtons.remove(button)
        }
    }

    fun onRelease(button: Int): Boolean = pendingButtons.remove(button)

    fun onPopupRepositioned() {
        pendingButtons.clear()
    }
}
