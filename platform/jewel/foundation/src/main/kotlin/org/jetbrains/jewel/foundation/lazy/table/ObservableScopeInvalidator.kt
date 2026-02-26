// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import kotlin.jvm.JvmInline

/**
 * Simple wrapper over a mutable state which allows to invalidate an observable scope. We might consider providing
 * something like this in the public api in the future.
 */
@JvmInline
internal value class ObservableScopeInvalidator(
    private val state: MutableState<Unit> = mutableStateOf(Unit, neverEqualPolicy())
) {
    fun attachToScope() {
        state.value
    }

    fun invalidateScope() {
        state.value = Unit
    }
}
