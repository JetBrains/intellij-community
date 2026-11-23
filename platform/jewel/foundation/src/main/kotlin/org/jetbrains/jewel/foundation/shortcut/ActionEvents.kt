// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** Input source that triggered an action invocation. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface ActionTrigger {
    /** Action triggered from a keyboard shortcut or key sequence [sequence]. */
    public data class Keyboard(
        /** The matching key sequence, or `null` when invoked directly without one. */
        val sequence: JewelKeySequence?
    ) : ActionTrigger

    /** Action triggered by pointer input on an action-bound control. */
    public data object Pointer : ActionTrigger

    /** Action triggered programmatically through [ActionInvoker]. */
    public data object Programmatic : ActionTrigger
}

/**
 * One completed Jewel-owned invocation. "Executed" has one narrow meaning: a resolved Jewel handler returned normally.
 * It is not a business-operation completion signal. Actions mapped to existing IJPL actions never emit here — observe
 * those through ordinary platform facilities.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public data class ActionInvocation(
    /** Identifier of the invoked action, or `null` for an anonymous shortcut claim. */
    val actionId: JewelActionId?,
    /** Key sequence that matched the invocation, or `null` for non-keyboard triggers. */
    val sequence: JewelKeySequence?,
    /** Input source that triggered the invocation. */
    val trigger: ActionTrigger,
)

/** Host-neutral observation point for Presentation Assistant-style overlays, logging, and tests. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ActionEventSource {
    /** Stream of completed Jewel-owned invocations. */
    public val invocations: SharedFlow<ActionInvocation>
}

/** Mutable [ActionEventSource] backed by a bounded replay-free buffer. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class MutableActionEventSource : ActionEventSource {
    private val _invocations = MutableSharedFlow<ActionInvocation>(extraBufferCapacity = 64)

    override val invocations: SharedFlow<ActionInvocation> = _invocations.asSharedFlow()

    /** Emits exactly one event per completed invocation; drops (never blocks) if nobody keeps up. */
    public fun emit(invocation: ActionInvocation) {
        _invocations.tryEmit(invocation)
    }
}

/** Reason an [ActionInvoker.invoke] request did not dispatch an action. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public enum class ActionDispatchRejection {
    /** No action is registered under the requested identifier. */
    Unregistered,

    /** No focused Jewel binding handles the requested action. */
    NoFocusedBinding,

    /** A focused binding exists, but it is currently disabled. */
    Disabled,

    /** The host accepted the action as registered, but rejected or ignored its execution. */
    RejectedByHost,

    /** No shortcut host is attached to the active surface. */
    HostUnavailable,
}

/** Outcome returned by [ActionInvoker.invoke]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface ActionDispatchResult {
    /** The action was accepted for dispatch. */
    public data object Dispatched : ActionDispatchResult

    /** The action could not be dispatched for [reason]. */
    public data class Rejected(
        /** Why the dispatch request was rejected. */
        val reason: ActionDispatchRejection
    ) : ActionDispatchResult
}

/**
 * Requests normal host action execution. In standalone this resolves the focused Jewel binding; the IJPL bridge routes
 * through `ActionManager.tryToExecute` so platform update/enablement stays authoritative.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ActionInvoker {
    /** Attempts to invoke [action] using the given [trigger]. */
    public fun invoke(action: JewelAction, trigger: ActionTrigger = ActionTrigger.Programmatic): ActionDispatchResult
}
