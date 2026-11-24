// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * An update flow that limits its updates to one in [minimalUpdateMillis]. An update in that time is not lost: it comes
 * at the end of that time, so the last update always reaches the collectors.
 */
abstract class MutableIconUpdateFlowBase : MutableIconUpdateFlow {
    private val updateCounter = AtomicInteger()

    /**
     * The updates. The flow keeps the last update, so an update does not wait for a collector, and a collector that
     * starts later still gets it. For example, a Compose icon starts to collect after its first measure.
     */
    private val underlyingFlow = MutableSharedFlow<Int>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The time of the last update. It starts at 0, so the first update is not limited. */
    protected var stopwatch: Long = 0L
    protected val minimalUpdateMillis: Long = 1000L / 60L // Use actual fps?

    protected fun handleRateLimiting(): Boolean {
        if (System.currentTimeMillis() - stopwatch < minimalUpdateMillis) return true
        stopwatch = System.currentTimeMillis()
        return false
    }

    override fun triggerUpdate() {
        if (handleRateLimiting()) {
            // a delayed update checks the limit again, so the updates in one limit come as one update at its end
            val elapsed = System.currentTimeMillis() - stopwatch
            triggerDelayedUpdate((minimalUpdateMillis - elapsed).coerceAtLeast(1L))
            return
        }
        val updateId = updateCounter.incrementAndGet()
        underlyingFlow.tryEmit(updateId)
    }

    override fun triggerDelayedUpdate(delay: Long) {
        underlyingFlow.emitDelayed(delay, updateCounter.incrementAndGet())
    }

    protected abstract fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int)

    override suspend fun collect(collector: FlowCollector<Int>) {
        underlyingFlow.collect(collector)
    }
}
