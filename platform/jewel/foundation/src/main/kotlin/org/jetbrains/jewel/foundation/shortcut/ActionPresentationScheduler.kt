// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Demand-driven presentation sampling: controls never call an update from composition; they register demand for an
 * action ID and observe an equality-gated [StateFlow]. A poll re-resolves only the actions that currently have
 * collectors and publishes only when the sample differs — `StateFlow`'s conflation makes an unchanged poll invalidate
 * nothing, so no recomposition happens for unchanged state.
 *
 * The host decides when to poll: focus changes, dispatches, keymap edits, and explicit [invalidate] calls. There is
 * deliberately no internal timer; the IJPL bridge rides the platform's action-update cadence instead (bridge slice),
 * and standalone hosts poll on their own signals.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class ActionPresentationScheduler(private val resolve: (JewelActionId) -> ActionPresentation) {
    private class Entry(initial: ActionPresentation) {
        val flow = MutableStateFlow(initial)
        var demand: Int = 0
    }

    private val entries = ConcurrentHashMap<JewelActionId, Entry>()

    /** Registers demand and returns the equality-gated sample flow. Pair with [release]. */
    public fun acquire(actionId: JewelActionId): StateFlow<ActionPresentation> {
        entries
            .computeIfPresent(actionId) { _, existing -> existing.also { it.demand++ } }
            ?.let {
                return it.flow.asStateFlow()
            }

        // Resolve initial sample outside any ConcurrentHashMap bucket lock to avoid reentrant contention.
        val initial = resolve(actionId)
        var entry: Entry? = null
        entries.compute(actionId) { _, existing ->
            (existing ?: Entry(initial)).also {
                it.demand++
                entry = it
            }
        }
        return checkNotNull(entry) { "Compute returned null for action $actionId" }.flow.asStateFlow()
    }

    /** Releases one demand registration for [actionId] and drops its sample flow when demand reaches zero. */
    public fun release(actionId: JewelActionId) {
        entries.computeIfPresent(actionId) { _, entry ->
            entry.demand--
            if (entry.demand <= 0) null else entry
        }
    }

    /** Re-samples one action (or all with active demand) and publishes only changed samples. */
    public fun invalidate(actionId: JewelActionId? = null) {
        if (actionId != null) {
            entries[actionId]?.let { it.flow.value = resolve(actionId) }
        } else {
            for ((id, entry) in entries) entry.flow.value = resolve(id)
        }
    }

    /** Returns the number of action IDs currently observed by at least one collector. */
    public fun activeDemandCount(): Int = entries.size
}

/**
 * Holds [scheduler] demand across composition lifecycles, releasing on both [onForgotten] and [onAbandoned] so an
 * aborted or abandoned composition cannot leak active polling entries.
 */
private class PresentationDemandHolder(
    private val scheduler: ActionPresentationScheduler,
    private val actionId: JewelActionId,
) : RememberObserver {
    val flow: StateFlow<ActionPresentation> = scheduler.acquire(actionId)
    private var released = false

    override fun onRemembered() = Unit

    override fun onForgotten() {
        releaseOnce()
    }

    override fun onAbandoned() {
        releaseOnce()
    }

    private fun releaseOnce() {
        if (!released) {
            released = true
            scheduler.release(actionId)
        }
    }
}

/**
 * Collects this action's presentation while in composition, registering demand with [scheduler] and releasing it on
 * dispose or abandoned composition. Recomposition occurs only when the sample changes by equality.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun JewelAction.collectPresentationAsState(scheduler: ActionPresentationScheduler): State<ActionPresentation> {
    val holder = remember(scheduler, id) { PresentationDemandHolder(scheduler, id) }
    return holder.flow.collectAsState()
}

/**
 * Collects one projection of this action's presentation, gated by the projection's own equality: a control observing
 * only `enabled` does not recompose when the text changes. The [selector] must be pure; the latest lambda is used
 * without restarting collection when the composable recomposes with a new instance.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun <T> JewelAction.collectPresentationAsState(
    scheduler: ActionPresentationScheduler,
    selector: (ActionPresentation) -> T,
): State<T> {
    val holder = remember(scheduler, id) { PresentationDemandHolder(scheduler, id) }
    val presentationState = holder.flow.collectAsState()
    val currentSelectorState = rememberUpdatedState(selector)
    return remember(holder) { derivedStateOf { currentSelectorState.value(presentationState.value) } }
}
