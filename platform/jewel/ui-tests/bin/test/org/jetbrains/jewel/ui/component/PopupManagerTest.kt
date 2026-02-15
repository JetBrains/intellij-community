package org.jetbrains.jewel.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupManagerTest {
    @Test
    fun `should start with the popup not visible`() {
        val popupManager = PopupManager()
        assertFalse(popupManager.isPopupVisible.value)
    }

    @Test
    fun `should toggle the visibility correctly`() {
        val popupManager = PopupManager()

        popupManager.togglePopupVisibility()
        assertTrue(popupManager.isPopupVisible.value)

        popupManager.togglePopupVisibility()
        assertFalse(popupManager.isPopupVisible.value)

        popupManager.togglePopupVisibility()
        assertTrue(popupManager.isPopupVisible.value)
    }

    @Test
    fun `should set the visibility correctly`() {
        val popupManager = PopupManager()

        popupManager.setPopupVisible(true)
        assertTrue(popupManager.isPopupVisible.value)

        popupManager.setPopupVisible(false)
        assertFalse(popupManager.isPopupVisible.value)

        popupManager.setPopupVisible(false)
        assertFalse(popupManager.isPopupVisible.value)

        popupManager.setPopupVisible(true)
        assertTrue(popupManager.isPopupVisible.value)

        popupManager.setPopupVisible(true)
        assertTrue(popupManager.isPopupVisible.value)
    }

    @Test
    fun `should only call onPopupVisibilityChange when the visibility actually changes`() {
        var callCount = 0
        val popupManager = PopupManager({ callCount++ })

        assertEquals(0, callCount)

        popupManager.setPopupVisible(true)
        assertEquals(1, callCount)

        popupManager.setPopupVisible(false)
        assertEquals(2, callCount)

        popupManager.setPopupVisible(false)
        assertEquals(2, callCount)

        popupManager.setPopupVisible(true)
        assertEquals(3, callCount)

        popupManager.setPopupVisible(true)
        assertEquals(3, callCount)
    }

    @Test
    fun `should consider two instances equals when their name and state are equal`() {
        var first = PopupManager()
        var second = PopupManager()
        assertTrue(first == second)

        // Same name, same state (false)
        first = PopupManager(name = "banana")
        second = PopupManager(name = "banana")
        assertTrue(first == second)

        // Same name, same state (true)
        first.togglePopupVisibility()
        second.togglePopupVisibility()
        assertTrue(first == second)
    }

    @Suppress("UnusedExpression") // Used to make sure the lambdas are different
    @Test
    fun `should ignore onPopupVisibilityChange when checking equality`() {
        val first = PopupManager({ "first" })
        val second = PopupManager({ "second" })
        assertTrue(first == second)
    }

    @Test
    fun `should have same hashcode when name and state are equal`() {
        var first = PopupManager()
        var second = PopupManager()
        assertTrue(first.hashCode() == second.hashCode())

        // Same name, same state (false)
        first = PopupManager(name = "banana")
        second = PopupManager(name = "banana")
        assertTrue(first.hashCode() == second.hashCode())

        // Same name, same state (true)
        first.togglePopupVisibility()
        second.togglePopupVisibility()
        assertTrue(first.hashCode() == second.hashCode())
    }

    @Suppress("UnusedExpression") // Used to make sure the lambdas are different
    @Test
    fun `should ignore onPopupVisibilityChange when computing hashcode`() {
        val first = PopupManager({ "first" })
        val second = PopupManager({ "second" })
        assertTrue(first.hashCode() == second.hashCode())
    }
}
