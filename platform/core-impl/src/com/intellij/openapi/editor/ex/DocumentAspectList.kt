// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.DocumentAspectListImpl
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Contract

/**
 * Immutable collection of [DocumentAspect]s of a [DocumentText], keyed by [Key] identity
 */
internal interface DocumentAspectList {

  @Contract(pure = true)
  fun get(key: Key<out DocumentAspect>): DocumentAspect?

  /**
   * Returns a list where [key] is associated with [aspect], replacing the current association if any
   */
  @Contract(pure = true)
  fun add(key: Key<out DocumentAspect>, aspect: DocumentAspect): DocumentAspectList

  /**
   * Returns a list without the aspect associated with [key]
   */
  @Contract(pure = true)
  fun remove(key: Key<out DocumentAspect>): DocumentAspectList

  /**
   * Returns a list where every aspect is replaced with the result of [action], keeping the keys
   */
  @Contract(pure = true)
  fun transform(action: (DocumentAspect) -> DocumentAspect): DocumentAspectList

  companion object {
    fun empty(): DocumentAspectList = DocumentAspectListImpl.EMPTY
  }
}
