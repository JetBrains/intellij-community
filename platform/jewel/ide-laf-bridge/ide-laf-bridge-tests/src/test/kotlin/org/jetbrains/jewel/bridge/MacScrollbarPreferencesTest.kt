package org.jetbrains.jewel.bridge

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.ui.EDT
import java.lang.foreign.MemorySegment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

internal class MacScrollbarPreferencesTest {
    @Before
    fun requireMacOs() {
        assumeTrue(SystemInfoRt.isMac)
    }

    @Test(timeout = 30_000)
    fun `native reads match the platform preferences`() {
        withNativePool {
            val style = Foundation.invoke("NSScroller", "preferredScrollerStyle").toLong()
            val defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
            Foundation.invoke(defaults, "synchronize")
            val expectedBehavior =
                Foundation.invoke(defaults, "boolForKey:", Foundation.nsString("AppleScrollerPagingBehavior"))
                    .booleanValue()
            assertEquals(style, FfmMacScrollbarPreferences.getPreferredStyle())
            assertTrue(
                "The native click behavior must match the platform",
                expectedBehavior == FfmMacScrollbarPreferences.isJumpToSpot(),
            )
        }
    }

    @Test(timeout = 30_000)
    fun `observers remain registered and close independently`() {
        val firstCalls = AtomicInteger()
        val secondCalls = AtomicInteger()
        val onEdt = AtomicBoolean(true)
        FfmMacScrollbarPreferences.observeChanges { firstCalls.incrementAndGet() }
            .use { first ->
                FfmMacScrollbarPreferences.observeChanges {
                        secondCalls.incrementAndGet()
                        if (!EDT.isCurrentThreadEdt()) onEdt.set(false)
                    }
                    .use { second ->
                        repeat(3) {
                            postStyleChange()
                            drainEdt()
                        }
                        assertEquals(3, firstCalls.get())
                        assertEquals(3, secondCalls.get())
                        first.close()
                        first.close()
                        postStyleChange()
                        drainEdt()
                        assertEquals(3, firstCalls.get())
                        assertEquals(4, secondCalls.get())
                        second.close()
                        postStyleChange()
                        drainEdt()
                        assertEquals(4, secondCalls.get())
                        assertTrue(onEdt.get())
                    }
            }
    }

    @Test(timeout = 30_000)
    fun `behavior notifications read both values without changing persistent preferences`() {
        for (jumpToSpot in listOf(false, true)) {
            withPagingBehavior(jumpToSpot) {
                val delivered = CompletableFuture<Boolean>()
                FfmMacScrollbarPreferences.observeChanges {
                        try {
                            assertTrue(EDT.isCurrentThreadEdt())
                            delivered.complete(FfmMacScrollbarPreferences.isJumpToSpot())
                        } catch (failure: AssertionError) {
                            delivered.completeExceptionally(failure)
                        }
                    }
                    .use { observer ->
                        deliverNotification(observer)
                        assertEquals(jumpToSpot, delivered.get(10, TimeUnit.SECONDS))
                    }
            }
        }
    }

    @Test(timeout = 30_000)
    fun `closing an observer cancels a queued notification`() {
        val calls = AtomicInteger()
        val completed = CompletableFuture<Unit>()
        FfmMacScrollbarPreferences.observeChanges { calls.incrementAndGet() }
            .use { observer ->
                SwingUtilities.invokeLater {
                    completed.completeWith(
                        runCatching {
                            deliverNotification(observer)
                            observer.close()
                        }
                    )
                }
                completed.get(10, TimeUnit.SECONDS)
                drainEdt()
                assertEquals(0, calls.get())
            }
    }

    @Test(timeout = 30_000)
    fun `listener failures do not escape the native callback or stop other observers`() {
        val calls = AtomicInteger()
        FfmMacScrollbarPreferences.observeChanges { error("Expected listener failure") }
            .use {
                FfmMacScrollbarPreferences.observeChanges { calls.incrementAndGet() }
                    .use {
                        repeat(2) {
                            postStyleChange()
                            drainEdt()
                        }
                        assertEquals(2, calls.get())
                    }
            }
    }

    private fun postStyleChange() {
        withNativePool {
            val center = Foundation.invoke("NSNotificationCenter", "defaultCenter")
            Foundation.invoke(
                center,
                "postNotificationName:object:",
                Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
                ID.NIL,
            )
        }
    }

    private fun deliverNotification(observer: AutoCloseable) {
        val field = observer.javaClass.getDeclaredField("delegate")
        field.isAccessible = true
        val delegate = field.get(observer) as MemorySegment
        withNativePool { Foundation.invoke(ID(delegate.address()), "notificationReceived:", ID.NIL) }
    }

    private fun withPagingBehavior(jumpToSpot: Boolean, action: () -> Unit) {
        withNativePool {
            val defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
            val domain = Foundation.nsString("NSArgumentDomain")
            val original = Foundation.invoke(defaults, "volatileDomainForName:", domain)
            Foundation.invoke(original, "retain")
            val replacement = Foundation.invoke(original, "mutableCopy")
            try {
                val value = Foundation.invoke("NSNumber", "numberWithBool:", if (jumpToSpot) 1 else 0)
                Foundation.invoke(
                    replacement,
                    "setObject:forKey:",
                    value,
                    Foundation.nsString("AppleScrollerPagingBehavior"),
                )
                Foundation.invoke(defaults, "setVolatileDomain:forName:", replacement, domain)
                action()
            } finally {
                Foundation.invoke(defaults, "setVolatileDomain:forName:", original, domain)
                Foundation.invoke(replacement, "release")
                Foundation.invoke(original, "release")
            }
        }
    }

    private fun <T> withNativePool(action: () -> T): T {
        val pool = Foundation.NSAutoreleasePool()
        try {
            return action()
        } finally {
            pool.drain()
        }
    }

    private fun drainEdt() {
        val completion = CountDownLatch(1)
        SwingUtilities.invokeLater { completion.countDown() }
        assertTrue("The EDT must process the notification", completion.await(10, TimeUnit.SECONDS))
    }

    private fun <T> CompletableFuture<T>.completeWith(result: Result<T>) {
        result.fold(::complete, ::completeExceptionally)
    }
}
