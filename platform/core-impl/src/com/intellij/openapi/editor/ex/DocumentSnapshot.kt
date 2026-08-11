// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable self-consistent snapshot of the whole document state.
 *
 * A snapshot owns the document [text] and is the place for the state derived from it that has to stay
 * consistent with the text across every change. A document core publishes a new snapshot atomically,
 * so a reader observes the text and everything derived from it from the same document version.
 *
 * @see DocumentCore.snapshot
 */
@ApiStatus.Internal
interface DocumentSnapshot {

  /**
   * Returns the text of this snapshot
   */
  @Contract(pure = true)
  fun text(): DocumentText

  /**
   * Returns a snapshot carrying [text], or `this` when [text] is already the text instance of this snapshot.
   *
   * Returning `this` for an unchanged text keeps snapshot identity stable, which callers depend on:
   * a metadata update that changes nothing leaves the core's snapshot field pointing at the same instance,
   * and caches keyed by snapshot identity, such as the frozen document of [DocumentCore.frozen], keep hitting.
   */
  @Contract(pure = true)
  fun withText(text: DocumentText): DocumentSnapshot

  /**
   * Returns a human-readable dump of the snapshot state, used for diagnostics only
   */
  @Contract(pure = true)
  fun dumpState(): String
}
