// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Contract

/**
 * Immutable collection of [DocumentSputnik]s of a [DocumentSnapshot], keyed by [Key] identity
 */
internal interface DocumentSputniks {

  @Contract(pure = true)
  fun get(key: Key<out DocumentSputnik>): DocumentSputnik?

  /**
   * Returns a list where [key] is associated with [sputnik], replacing the current association if any
   */
  @Contract(pure = true)
  fun add(key: Key<out DocumentSputnik>, sputnik: DocumentSputnik): DocumentSputniks

  /**
   * Returns a list without the sputnik associated with [key]
   */
  @Contract(pure = true)
  fun remove(key: Key<out DocumentSputnik>): DocumentSputniks

  /**
   * Returns a list where every sputnik is replaced with the result of [action], keeping the keys
   */
  @Contract(pure = true)
  fun transform(action: (DocumentSputnik) -> DocumentSputnik): DocumentSputniks
}
