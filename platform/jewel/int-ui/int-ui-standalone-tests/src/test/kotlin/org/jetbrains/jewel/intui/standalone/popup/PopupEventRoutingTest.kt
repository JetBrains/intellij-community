// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import java.awt.Panel
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class PopupEventRoutingTest {
    @Test
    fun `focusable visible popup receives keys while owner remains active`() {
        assertTrue(
            PopupEventRouting.shouldDispatchKeyEventToPopup(
                popupFocusable = true,
                popupVisible = true,
                popupActive = false,
                ownerActive = true,
            )
        )
    }

    @Test
    fun `popup does not receive keys when neither popup nor owner is active`() {
        assertFalse(
            PopupEventRouting.shouldDispatchKeyEventToPopup(
                popupFocusable = true,
                popupVisible = true,
                popupActive = false,
                ownerActive = false,
            )
        )
    }

    @Test
    fun `resolved outside mouse press dismisses non-focusable popup`() {
        assertTrue(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = false,
                popupActive = false,
                eventInPopupOrOwnedChild = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `owner-window press leaves focusable popup dismissal for matching release`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = true,
                popupActive = false,
                eventInPopupOrOwnedChild = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `other-window press dismisses inactive focusable popup`() {
        assertTrue(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = true,
                popupActive = false,
                eventInPopupOrOwnedChild = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = false,
            )
        )
    }

    @Test
    fun `active focusable popup leaves outside mouse press for focus loss`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = true,
                popupActive = true,
                eventInPopupOrOwnedChild = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `inside or owned-child mouse press does not dismiss popup`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = false,
                popupActive = false,
                eventInPopupOrOwnedChild = true,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `inside popup bounds mouse press does not dismiss popup`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnMousePress(
                popupFocusable = false,
                popupActive = false,
                eventInPopupOrOwnedChild = false,
                eventInsidePopupBounds = true,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `fresh press tracker does not match context-menu opening release`() {
        val tracker = PopupOutsidePressTracker()

        assertFalse(tracker.onRelease(button = 3))
    }

    @Test
    fun `press tracker preserves a pending button across another button release`() {
        val tracker = PopupOutsidePressTracker()
        tracker.onPress(button = 3, isOwnerOutsidePress = true)

        assertFalse(tracker.onRelease(button = 1))
        assertTrue(tracker.onRelease(button = 3))
    }

    @Test
    fun `press tracker recognizes overlapping qualifying button gestures independently`() {
        val tracker = PopupOutsidePressTracker()
        tracker.onPress(button = 1, isOwnerOutsidePress = true)
        tracker.onPress(button = 3, isOwnerOutsidePress = true)

        assertTrue(tracker.onRelease(button = 1))
        assertTrue(tracker.onRelease(button = 3))
    }

    @Test
    fun `nonqualifying press clears stale provenance for the same button`() {
        val tracker = PopupOutsidePressTracker()
        tracker.onPress(button = 3, isOwnerOutsidePress = true)
        tracker.onPress(button = 3, isOwnerOutsidePress = false)

        assertFalse(tracker.onRelease(button = 3))
    }

    @Test
    fun `popup reposition clears press provenance from its previous geometry`() {
        val tracker = PopupOutsidePressTracker()
        tracker.onPress(button = 3, isOwnerOutsidePress = true)

        tracker.onPopupRepositioned()

        assertFalse(tracker.onRelease(button = 3))
    }

    @Test
    fun `popup focus loss dismisses focusable popup when focus leaves popup family`() {
        assertTrue(
            PopupEventRouting.shouldDismissOnFocusLoss(
                dismissOnClickOutside = true,
                popupFocusable = true,
                isFocusLostEvent = true,
                focusLostWindowIsPopup = true,
                oppositeWindowInPopupOrOwnedChild = false,
                oppositeWindowIsPopupOwner = false,
            )
        )
    }

    @Test
    fun `owner focus loss does not dismiss popup`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnFocusLoss(
                dismissOnClickOutside = true,
                popupFocusable = true,
                isFocusLostEvent = true,
                focusLostWindowIsPopup = false,
                oppositeWindowInPopupOrOwnedChild = false,
                oppositeWindowIsPopupOwner = false,
            )
        )
    }

    @Test
    fun `popup focus loss does not dismiss when focus moves into popup family`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnFocusLoss(
                dismissOnClickOutside = true,
                popupFocusable = true,
                isFocusLostEvent = true,
                focusLostWindowIsPopup = true,
                oppositeWindowInPopupOrOwnedChild = true,
                oppositeWindowIsPopupOwner = false,
            )
        )
    }

    @Test
    fun `popup focus loss does not dismiss when focus moves to popup owner`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnFocusLoss(
                dismissOnClickOutside = true,
                popupFocusable = true,
                isFocusLostEvent = true,
                focusLostWindowIsPopup = true,
                oppositeWindowInPopupOrOwnedChild = false,
                oppositeWindowIsPopupOwner = true,
            )
        )
    }

    @Test
    fun `popup focus loss does not dismiss non-focusable popup`() {
        assertFalse(
            PopupEventRouting.shouldDismissOnFocusLoss(
                dismissOnClickOutside = true,
                popupFocusable = false,
                isFocusLostEvent = true,
                focusLostWindowIsPopup = true,
                oppositeWindowInPopupOrOwnedChild = false,
                oppositeWindowIsPopupOwner = false,
            )
        )
    }

    @Test
    fun `owner-active redispatch consumes unhandled plain keys`() {
        assertTrue(
            PopupEventRouting.shouldConsumeOwnerActiveKeyAfterRedispatch(
                redispatchedEventConsumed = false,
                isDismissRequest = false,
                isModifiedDismissRequest = false,
            )
        )
    }

    @Test
    fun `owner-active redispatch leaves modified escape for global shortcut`() {
        assertFalse(
            PopupEventRouting.shouldConsumeOwnerActiveKeyAfterRedispatch(
                redispatchedEventConsumed = false,
                isDismissRequest = false,
                isModifiedDismissRequest = true,
            )
        )
    }

    @Test
    fun `owner-active redispatch consumes handled popup keys`() {
        assertTrue(
            PopupEventRouting.shouldConsumeOwnerActiveKeyAfterRedispatch(
                redispatchedEventConsumed = true,
                isDismissRequest = false,
                isModifiedDismissRequest = true,
            )
        )
    }

    @Test
    fun `popup key event target prefers child with key listener`() {
        val container = Panel()
        val child = Panel().apply { addKeyListener(object : KeyAdapter() {}) }
        container.add(child)

        assertTrue(container.findPopupKeyEventTarget() === child)
    }

    @Test
    fun `mouse release without matching observed press does not defer dismissal`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = false,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release matching observed owner press defers dismissal`() {
        assertTrue(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release does not defer when popup is not focusable`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = false,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release does not defer when popup opts out of click-outside dismiss`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = false,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse press does not trigger deferred dismissal`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = false,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release inside popup component tree does not defer dismissal`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = true,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release inside popup bounds does not defer dismissal`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = true,
                eventComponentInOwnerWindow = true,
            )
        )
    }

    @Test
    fun `mouse release outside owner window does not defer dismissal`() {
        assertFalse(
            PopupEventRouting.shouldDeferDismissOnMouseReleased(
                popupFocusable = true,
                dismissOnClickOutside = true,
                isMouseReleasedEvent = true,
                matchingOwnerOutsidePressObserved = true,
                eventComponentIsPopupDescendant = false,
                eventInsidePopupBounds = false,
                eventComponentInOwnerWindow = false,
            )
        )
    }

    @Test
    fun `target key listener consumption is reflected after direct listener dispatch`() {
        val target =
            Panel().apply {
                addKeyListener(
                    object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) = e.consume()
                    }
                )
            }
        val event = KeyEvent(target, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a')

        target.dispatchPopupKeyEventToTargetListeners(event)

        assertTrue(event.isConsumed)
    }
}
