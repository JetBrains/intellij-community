// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

abstract class MutableIconUpdateFlowBase : MutableIconUpdateFlow {
    private val updateCounter = AtomicInteger()
    private val underlayingFlow = MutableSharedFlow<Int>()
    protected var stopwatch: Long = System.currentTimeMillis()
    protected val minimalUpdateMillis: Long = 1000L / 60L // Use actual fps?

    protected fun handleRateLimiting(): Boolean {
        if (System.currentTimeMillis() - stopwatch < minimalUpdateMillis) return true
        stopwatch = System.currentTimeMillis()
        return false
    }

    override fun triggerUpdate() {
        if (handleRateLimiting()) return
        val updateId = updateCounter.incrementAndGet()
        underlayingFlow.tryEmit(updateId)
    }

    override fun triggerDelayedUpdate(delay: Long) {
        underlayingFlow.emitDelayed(delay, updateCounter.incrementAndGet())
    }

    protected abstract fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int)

    override suspend fun collect(collector: FlowCollector<Int>) {
        underlayingFlow.collect(collector)
    }
}
