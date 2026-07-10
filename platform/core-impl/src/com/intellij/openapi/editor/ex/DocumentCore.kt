// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Internal abstraction used to implement [com.intellij.openapi.editor.impl.DocumentImpl].
 *
 * This interface is designed to hide the actual implementation of the document.
 */
@ApiStatus.Internal
interface DocumentCore {

  /**
   * Returns an immutable, self-consistent snapshot of the current document content.
   *
   * NOTE: this is a performance-critical method; ideally, implementation should be a direct volatile
   * field read, without extra indirection, allocation, or synchronization
   */
  @Contract(pure = true)
  fun snapshot(): DocumentSnapshot

  /**
   * Returns a live character sequence backed by the current document [snapshot]
   */
  @Contract(pure = true)
  fun live(): CharSequence

  /**
   * Returns storage and lookup support for range markers and guarded blocks
   */
  @Contract(pure = true)
  fun tree(): DocumentRangeMarkerTree

  /**
   * Returns listener storage and notification dispatch support
   */
  @Contract(pure = true)
  fun dispatcher(): DocumentEventDispatcher

  /**
   * Returns the document write path used for all document snapshot changes
   */
  @Contract(pure = true)
  fun mutator(): DocumentMutator

  /**
   * Returns document-wide flags like whitespace stripping, line separator handling, and read-only checks
   */
  @Contract(pure = true)
  fun settings(): DocumentSettings

  /**
   * Returns frozen document based on current [snapshot].
   *
   * It is a bridge between internal [DocumentSnapshot] and public [DocumentEx]
   */
  @Contract(pure = true)
  fun frozen(): DocumentEx
}
