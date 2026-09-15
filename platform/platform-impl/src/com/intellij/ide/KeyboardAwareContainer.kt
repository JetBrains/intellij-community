// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.KeyEvent

/**
 * Ancestor-side counterpart of [KeyboardAwareFocusOwner]: lets a container keep [IdeKeyEventDispatcher] from running
 * keymap shortcuts for key events targeted at a focused descendant it cannot control.
 *
 * Some embedded UI toolkits (Compose, native web views, other canvas-based renderers) put AWT focus on an internal
 * component that the embedder cannot make implement [KeyboardAwareFocusOwner]. A container implementing this interface
 * makes the same decision on behalf of that descendant: before [IdeKeyEventDispatcher] starts matching a key event
 * against the keymap, it asks each implementing ancestor of the focus owner, innermost first, and stands down as soon as
 * one returns `true`.
 *
 * Contract:
 * - Implementors must be a [java.awt.Container] in the focus owner's ancestor chain. Discovery is structural: nothing is
 *   registered, and a container outside the chain is never asked. The chain is resolved once per focus owner and
 *   refreshed when the hierarchy above that owner changes.
 * - Returning `true` only skips shortcut matching in [IdeKeyEventDispatcher]. It does not consume the event, which
 *   continues through ordinary AWT dispatch, and it does not affect other [IdeEventQueue] dispatchers or AWT focus
 *   traversal, so delivery to the focused content is permitted but not guaranteed. Delivering the key to the focused
 *   content, and suppressing the `KEY_TYPED` that follows a claimed press, remain the embedder's responsibility.
 * - Returning `false` passes the question on to outer containers and then to normal processing.
 * - A container is consulted only when the dispatcher is idle. While the IDE is completing a multi-stroke shortcut, or
 *   swallowing the `KEY_TYPED` of a shortcut it just performed, the dispatcher keeps the event and does not ask. A claim
 *   must therefore begin at a chord's first stroke.
 * - The focus owner itself is asked first: a [KeyboardAwareFocusOwner] that skips the event wins before any ancestor.
 * - [skipKeyEventDispatcher] runs on the EDT, potentially for every key event while a descendant is focused. It must be
 *   fast and free of side effects: it must not block, consume or modify the event, move focus, or dispatch events. An exception
 *   propagates out of the dispatcher and is reported by [IdeEventQueue], and the event is then not delivered. Return
 *   `true` only for events the focused content explicitly claims; returning it unconditionally disables all keymap
 *   shortcuts while the descendant is focused.
 */
@ApiStatus.Experimental
interface KeyboardAwareContainer {
  /**
   * @param focusOwner the focused descendant the event is targeted at
   * @param event the key event about to be matched against the keymap by [IdeKeyEventDispatcher]
   * @return `true` to skip keymap shortcut processing for this event and permit ordinary AWT dispatch of it
   */
  fun skipKeyEventDispatcher(focusOwner: Component, event: KeyEvent): Boolean
}
