package org.jetbrains.jewel.bridge

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.ui.EDT
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    fun `getPreferredStyle reads zero, one, and 64-bit NSInteger values from target class`() {
        for (expected in listOf(STYLE_LEGACY, STYLE_OVERLAY, WIDE_NS_INTEGER)) {
            ScrollerStub.stubbedStyle.set(expected)
            assertEquals(expected, FfmMacScrollbarPreferences.getPreferredStyle(ScrollerStub.fakeScrollerClass))
        }
    }

    @Test(timeout = 30_000)
    fun `native reads match the platform preferences`() {
        ObjCTestHelper.withAutoreleasePool {
            val (expectedStyle, actualStyle) = readConsistentPreferredStyle()
            assertTrue(
                "NSScroller.preferredScrollerStyle must be Legacy (0) or Overlay (1), got $actualStyle",
                actualStyle == STYLE_LEGACY || actualStyle == STYLE_OVERLAY,
            )
            assertEquals(expectedStyle, actualStyle)

            val expectedBehavior = ObjCTestHelper.readUserDefaultsBool(ScrollerStub.strPagingBehavior)
            assertEquals(
                "The native click behavior must match the platform",
                expectedBehavior,
                FfmMacScrollbarPreferences.isJumpToSpot(),
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

    private fun readConsistentPreferredStyle(): Pair<Long, Long> {
        // Under "Automatically based on mouse or trackpad", the first call to +[NSScroller preferredScrollerStyle]
        // returns a provisional value (1 = Overlay) while AppKit detects pointing devices asynchronously and can flip
        // it to 0 (Legacy) milliseconds later. Warm up first, then bracket the read so both sides observe one state.
        FfmMacScrollbarPreferences.getPreferredStyle()
        repeat(100) {
            val before = ScrollerStub.readLivePreferredScrollerStyle()
            val actual = FfmMacScrollbarPreferences.getPreferredStyle()
            val after = ScrollerStub.readLivePreferredScrollerStyle()
            if (before == after) return before to actual
            Thread.onSpinWait()
        }
        throw AssertionError("NSScroller.preferredScrollerStyle did not stabilize across 100 bracketed reads")
    }

    private fun postStyleChange() {
        ObjCTestHelper.postNotification(ScrollerStub.strStyleChanged)
    }

    private fun deliverNotification(observer: AutoCloseable) {
        val field = observer.javaClass.getDeclaredField("delegate")
        field.isAccessible = true
        val delegate = field.get(observer) as MemorySegment
        ObjCTestHelper.msgSendVoid(delegate, ScrollerStub.selNotificationReceived, MemorySegment.NULL)
    }

    private fun withPagingBehavior(jumpToSpot: Boolean, action: () -> Unit) {
        ObjCTestHelper.withArgumentDomainBool(ScrollerStub.strPagingBehavior, jumpToSpot, action)
    }

    private fun drainEdt() {
        val completion = CountDownLatch(1)
        SwingUtilities.invokeLater { completion.countDown() }
        assertTrue("The EDT must process the notification", completion.await(10, TimeUnit.SECONDS))
    }

    private fun <T> CompletableFuture<T>.completeWith(result: Result<T>) {
        result.fold(::complete, ::completeExceptionally)
    }

    private object ScrollerStub {
        val stubbedStyle: AtomicLong = AtomicLong(STYLE_LEGACY)

        private val nsScrollerClass: MemorySegment = ObjCTestHelper.objcClass("NSScroller")
        private val selPreferredScrollerStyle: MemorySegment = ObjCTestHelper.selector("preferredScrollerStyle")
        val selNotificationReceived: MemorySegment = ObjCTestHelper.selector("notificationReceived:")
        val strPagingBehavior: MemorySegment = ObjCTestHelper.nsString("AppleScrollerPagingBehavior")
        val strStyleChanged: MemorySegment = ObjCTestHelper.nsString("NSPreferredScrollerStyleDidChangeNotification")

        val fakeScrollerClass: MemorySegment by lazy {
            val callback =
                MethodHandles.lookup()
                    .findStatic(
                        ScrollerStub::class.java,
                        "stubbedPreferredScrollerStyle",
                        MethodType.methodType(java.lang.Long.TYPE, MemorySegment::class.java, MemorySegment::class.java),
                    )
            ObjCTestHelper.registerClassWithLongClassMethod(
                classNamePrefix = "JewelTestScroller",
                selector = selPreferredScrollerStyle,
                callback = callback,
            )
        }

        fun readLivePreferredScrollerStyle(): Long =
            ObjCTestHelper.msgSendLong(nsScrollerClass, selPreferredScrollerStyle)

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun stubbedPreferredScrollerStyle(self: MemorySegment, selector: MemorySegment): Long = stubbedStyle.get()
    }

    private companion object {
        const val STYLE_LEGACY = 0L
        const val STYLE_OVERLAY = 1L
        const val WIDE_NS_INTEGER = 0x1_0000_0001L
    }
}
