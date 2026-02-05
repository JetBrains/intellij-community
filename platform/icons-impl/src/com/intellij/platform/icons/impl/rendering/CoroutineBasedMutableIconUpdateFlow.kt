// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.Icon
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class CoroutineBasedMutableIconUpdateFlow(private val coroutineScope: CoroutineScope, updateCallback: (Int) -> Unit) :
    MutableIconUpdateFlowBase(updateCallback) {
    override fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int) {
        coroutineScope.launch {
            delay(delay.milliseconds)
            if (handleRateLimiting()) return@launch
            emit(value)
            updateCallback(value)
        }
    }

    override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
        coroutineScope.launch { flow.collect { handler(it) } }
    }
}
