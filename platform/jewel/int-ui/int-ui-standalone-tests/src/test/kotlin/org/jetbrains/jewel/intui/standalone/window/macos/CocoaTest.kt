// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.MAC)
internal class CocoaTest {
    @BeforeEach
    fun reset() {
        notificationsReceived.set(0)
    }

    @Test
    fun `withAutoreleasePool rethrows the exception thrown by the block`() {
        val thrown =
            assertFailsWith<IllegalArgumentException> {
                withAutoreleasePool { throw IllegalArgumentException("get outta there it's gonna blow!") }
            }

        assertEquals("get outta there it's gonna blow!", thrown.message)
    }

    @Test
    fun `withAutoreleasePool returns the result of the block`() {
        assertEquals(42, withAutoreleasePool { 42 })
    }

    @Test
    fun `getDefaultCenter returns a notification center`() {
        assertNotEquals(MemorySegment.NULL, getDefaultCenter().segment)
    }

    @Test
    fun `getDistributedCenter returns a notification center`() {
        assertNotEquals(MemorySegment.NULL, getDistributedCenter().segment)
    }

    @Test
    fun `isOverlayScrollerStyle reads the preference without throwing`() {
        withAutoreleasePool { isOverlayScrollerStyle() }
    }

    @Test
    fun `isScrollbarTrackClickBehaviorJumpToSpot reads the preference without throwing`() {
        withAutoreleasePool { isScrollbarTrackClickBehaviorJumpToSpot() }
    }

    @Test
    fun `sendToWindowDelegateIfSupported does nothing for a nil window`() {
        sendToWindowDelegateIfSupported(ObjcObject(MemorySegment.NULL), "updateColors")
    }

    @Test
    fun `check that notification is received after registering observer`() {
        val callbackHandle = createCallback()
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "")
        val selector = selRegisterName("jewelTestNotificationReceived:")

        withAutoreleasePool {
            val notificationName = nsString("JewelCocoaTestNotification_$uniqueSuffix")
            val center = getDefaultCenter()
            val observerClass = createObserverClass("JewelCocoaTestObserver_$uniqueSuffix", selector, callbackHandle)
            val observer = newInstance(observerClass)
            try {
                addObserver(center, observer, selector, notificationName)

                postNotification(center, notificationName)
            } finally {
                removeObserver(center, observer)
                release(observer)
            }
        }

        assertEquals(1, notificationsReceived.get())
    }

    @Test
    fun `notification is not delivered after removeObserver`() {
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "")
        val selector = selRegisterName("jewelTestNotificationReceived:")

        withAutoreleasePool {
            val notificationName = nsString("JewelCocoaTestNotification_$uniqueSuffix")
            val center = getDefaultCenter()
            val observerClass = createObserverClass("JewelCocoaTestObserver_$uniqueSuffix", selector, createCallback())
            val observer = newInstance(observerClass)
            try {
                addObserver(center, observer, selector, notificationName)
                removeObserver(center, observer)

                postNotification(center, notificationName)
            } finally {
                release(observer)
            }
        }

        assertEquals(0, notificationsReceived.get())
    }

    @Test
    fun `notification posted before registering the observer is not delivered`() {
        val callbackHandle = createCallback()
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "")
        val selector = selRegisterName("jewelTestNotificationReceived:")

        withAutoreleasePool {
            val notificationName = nsString("JewelCocoaTestNotification_$uniqueSuffix")
            val center = getDefaultCenter()
            postNotification(center, notificationName)
            val observerClass = createObserverClass("JewelCocoaTestObserver_$uniqueSuffix", selector, callbackHandle)
            val observer = newInstance(observerClass)
            try {
                assertEquals(0, notificationsReceived.get())

                addObserver(center, observer, selector, notificationName)
                postNotification(center, notificationName)
                assertEquals(1, notificationsReceived.get())
            } finally {
                removeObserver(center, observer)
                release(observer)
            }
        }
    }

    @Test
    fun `addDistributedObserver registers an observer without throwing`() {
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "")
        val selector = selRegisterName("jewelTestNotificationReceived:")

        withAutoreleasePool {
            val className = "JewelCocoaTestDistributedObserver_$uniqueSuffix"
            val observer = newInstance(createObserverClass(className, selector, createCallback()))
            val center = getDistributedCenter()
            try {
                addDistributedObserver(
                    center,
                    observer,
                    selector,
                    nsString("JewelCocoaTestDistributedNotification_$uniqueSuffix"),
                    NS_NOTIFICATION_SUSPENSION_BEHAVIOR_COALESCE,
                )
            } finally {
                removeObserver(center, observer)
                release(observer)
            }
        }
    }

    private fun createCallback() =
        MethodHandles.lookup()
            .findStatic(
                CocoaTest::class.java,
                "testNotificationReceived",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )

    companion object {
        private val notificationsReceived = AtomicInteger()

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        private fun testNotificationReceived(
            self: MemorySegment,
            selector: MemorySegment,
            notification: MemorySegment,
        ) {
            notificationsReceived.incrementAndGet()
        }
    }
}
