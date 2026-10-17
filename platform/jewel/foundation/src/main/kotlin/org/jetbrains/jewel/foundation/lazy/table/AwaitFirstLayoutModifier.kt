// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnGloballyPositionedModifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A modifier that allows suspending execution until the first layout pass is completed.
 *
 * This modifier implements [OnGloballyPositionedModifier] to track when a composable is positioned for the first time.
 * It provides a mechanism to wait for the initial layout before performing operations that depend on layout information
 * being available.
 *
 * This is particularly useful in lazy layouts where certain operations need to be deferred until the layout has been
 * measured and positioned at least once.
 *
 * @see OnGloballyPositionedModifier
 */
internal class AwaitFirstLayoutModifier : OnGloballyPositionedModifier {
    private var wasPositioned = false
    private var continuation: Continuation<Unit>? = null

    /**
     * Suspends execution until the first layout pass is completed.
     *
     * If the composable has already been positioned, this function returns immediately. Otherwise, it suspends until
     * [onGloballyPositioned] is called for the first time.
     *
     * If multiple coroutines call this function before the first layout, only the most recent call will suspend, while
     * previous calls will be resumed immediately with the new call.
     */
    suspend fun waitForFirstLayout() {
        if (!wasPositioned) {
            val oldContinuation = continuation
            suspendCoroutine { continuation = it }
            oldContinuation?.resume(Unit)
        }
    }

    /**
     * Called when the composable is positioned globally.
     *
     * On the first positioning, this method resumes any suspended [waitForFirstLayout] coroutine and sets the
     * [wasPositioned] flag to prevent further suspensions.
     *
     * @param coordinates The global coordinates of the positioned composable (not used).
     */
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!wasPositioned) {
            wasPositioned = true
            continuation?.resume(Unit)
            continuation = null
        }
    }
}
