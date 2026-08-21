// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.e2e

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * The slice of UI automation the showcase scenarios need, so that one set of scenarios can run against both hosts Jewel
 * supports.
 *
 * Standalone backs this with Spectre's in-process automator against the sample app; the IDE backs it with Spectre
 * attached over the agent's IPC against DevKit's components showcase. The two automators are not source-compatible —
 * different node types, different method shapes, one suspending and one blocking — so without something like this the
 * two lanes would drift, and a bug fixed for one host could quietly persist in the other.
 *
 * Deliberately tiny, and deliberately free of both Spectre and IntelliJ Platform types: the standalone lane asserts
 * that no platform class reaches its runtime closure. The waits live here so both hosts fail the same way, naming what
 * *is* on screen: a wrong tag, a view that never switched and a surface the automator cannot see all look identical
 * without that.
 */
public interface ShowcaseAutomator {
    /** Whether anything with [tag] is on screen right now, across every surface the host knows about. */
    public suspend fun isPresent(tag: String): Boolean

    /** Every test tag currently on screen, for failure messages. */
    public suspend fun visibleTags(): List<String>

    /**
     * Clicks the node with [tag], leaving the pointer where it landed.
     *
     * Several scenarios depend on that: hover state is what makes a ComboBox suppress its pointer dismissal, and moving
     * the pointer away between actions would quietly change what is being tested.
     */
    public suspend fun click(tag: String)

    /** Clicks a node by its accessibility description, which is how the showcase's view switcher is addressable. */
    public suspend fun clickByContentDescription(description: String)

    /** Presses and releases [keyCode], an [java.awt.event.KeyEvent] `VK_` constant. */
    public suspend fun pressKey(keyCode: Int)

    /** Waits for the UI to settle, so a check does not race a recomposition or an animation. */
    public suspend fun waitForIdle()

    /** Waits until a node with [tag] exists, failing if it does not appear in time. */
    public suspend fun waitForNode(tag: String) {
        waitUntil("a node tagged '$tag' to appear") { isPresent(tag) }
    }

    /** Waits until nothing with [tag] is on screen, failing if it is still there in time. */
    public suspend fun waitUntilGone(tag: String) {
        waitUntil("a node tagged '$tag' to go away") { !isPresent(tag) }
    }

    /** Polls [condition] until it holds, failing after the wait budget with [what] and the tags then on screen. */
    public suspend fun waitUntil(what: String, condition: suspend () -> Boolean) {
        var waited = Duration.ZERO
        while (waited < WAIT_BUDGET) {
            if (condition()) return
            delay(POLL_INTERVAL)
            waited += POLL_INTERVAL
        }
        val tags = runCatching { visibleTags() }.getOrElse { listOf("<could not read the tree: ${it.message}>") }
        error("Timed out after $WAIT_BUDGET waiting for $what. Visible tags: $tags")
    }
}

/**
 * Runs one scenario, isolated: one that fails or throws must neither stop the others nor leave the UI in a state the
 * next one trips over. [cleanup] is the scenario group's way back to a known state, and its own failures are ignored.
 *
 * A scenario returns `null` when the behaviour held, or a description of what went wrong.
 */
public suspend fun ShowcaseAutomator.runIsolated(
    failures: MutableList<String>,
    name: String,
    cleanup: suspend (ShowcaseAutomator) -> Unit,
    scenario: suspend (ShowcaseAutomator) -> String?,
) {
    val outcome = runCatching { scenario(this) }
    val failure =
        outcome.getOrNull() ?: outcome.exceptionOrNull()?.let { "threw ${it::class.simpleName}: ${it.message}" }
    if (failure != null) failures += "$name: $failure"
    runCatching { cleanup(this) }
}

private val WAIT_BUDGET: Duration = 10.seconds
private val POLL_INTERVAL: Duration = 200.milliseconds
