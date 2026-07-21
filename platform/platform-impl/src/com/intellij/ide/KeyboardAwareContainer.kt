// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.KeyEvent

/**
 * Ancestor-side counterpart of [KeyboardAwareFocusOwner]: lets a container stop [IdeKeyEventDispatcher] from running
 * keymap shortcuts for key events sent to a focused descendant it does not control.
 *
 * Some embedded UI toolkits (Compose, native web views, other canvas-based renderers) put AWT focus on an internal
 * component that the embedder cannot make implement [KeyboardAwareFocusOwner]. A container that implements this
 * interface makes the same decision for that descendant. Before [IdeKeyEventDispatcher] matches a key event against the
 * keymap, it asks each implementing ancestor of the focus owner, innermost first, and stops as soon as one returns `true`.
 *
 * Contract:
 * - Implementors must be a [java.awt.Container] above the focus owner. Nothing is registered: a container that is not an
 *   ancestor is never asked. The chain is resolved once per focus owner and again when the hierarchy above it changes.
 * - Returning `true` only skips keymap matching in [IdeKeyEventDispatcher]. It does not consume the event and does not
 *   affect other [IdeEventQueue] dispatchers or focus traversal, so the event may still not reach the focused content.
 *   Delivering the key to that content, and suppressing the `KEY_TYPED` that follows a claimed press, is the embedder's job.
 * - Returning `false` passes the question to the outer containers and then to normal processing.
 * - A container is asked only when the dispatcher is idle. While the IDE completes a multi-stroke shortcut, or swallows
 *   the `KEY_TYPED` of a shortcut it just performed, it keeps the event. A claim must therefore start at a chord's first
 *   stroke.
 * - The focus owner is asked first: a [KeyboardAwareFocusOwner] that skips the event wins over any ancestor.
 * - [skipKeyEventDispatcher] runs on the EDT, possibly for every key event while a descendant is focused. It must be fast
 *   and have no side effects: no blocking, no consuming or changing the event, no focus changes, no event dispatch. An
 *   exception leaves the dispatcher, is reported by [IdeEventQueue], and the event is lost. Return `true` only for events
 *   the focused content claims; returning it for everything disables all keymap shortcuts while the descendant is focused.
 */
@ApiStatus.Experimental
interface KeyboardAwareContainer {
  /**
   * Decides whether [IdeKeyEventDispatcher] skips keymap matching for [event], which is about to be dispatched to
   * [focusOwner], a descendant of this container. Called on the EDT for every key event the dispatcher would otherwise
   * match, innermost container first, but only after the focus owner itself declined as a [KeyboardAwareFocusOwner], and
   * never while the IDE is completing a chord. Must be fast and have no side effects.
   *
   * @return `true` to skip keymap matching; the event is not consumed, and ordinary AWT dispatch continues. `false` passes
   * the question to the outer containers and then to normal processing.
   */
  fun skipKeyEventDispatcher(focusOwner: Component, event: KeyEvent): Boolean
}
