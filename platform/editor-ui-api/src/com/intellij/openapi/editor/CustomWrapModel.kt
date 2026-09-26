// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Model for managing custom (user-defined) soft wraps in an editor.
 * Custom wraps persist across automatic soft wrap recalculations.
 *
 * Not thread-safe.
 * All interface functions must be invoked from the EDT.
 *
 * @see Editor.getCustomWrapModel
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface CustomWrapModel {
  fun getWraps(): List<CustomWrap>
  fun getWrapsInRange(startOffset: Int, endOffset: Int): List<CustomWrap>
  fun getWrapsAtOffset(offset: Int): List<CustomWrap>
  fun hasWraps(): Boolean

  /**
   * [mutation] will be invoked exactly once.
   * Provided [Mutator] may only be used inside [mutation].
   */
  fun <T> runBatchMutation(mutation: Mutator.() -> T): T

  fun addListener(listener: Listener, disposable: Disposable)

  interface Mutator {
    /**
     * Adds a custom wrap at the specified offset.
     *
     * Custom wraps may not be adjacent to actual line breaks in the document.
     * Adding wrap at such offsets returns `null`.
     * If the wrap lands at such offset after document modifications,
     * it will be removed automatically (see [Listener.customWrapRemoved]).
     *
     * Note: Use custom wraps to break existing lines;
     * to insert empty lines use [InlayModel.addBlockElement].
     *
     * @param priority Only one custom wrap is rendered at a single offset. The lowest priority wins.
     * @param indentInColumns Non-negative number of columns to indent after the wrap.
     */
    fun addWrap(offset: Int, indentInColumns: Int = 0, priority: Int = 0): CustomWrap?
    /**
     * @return Whether [wrap] was removed as a result of this operation
     */
    fun removeWrap(wrap: CustomWrap): Boolean
  }

  interface Listener : EventListener {
    fun customWrapBatchMutationStarted() {}
    fun customWrapBatchMutationFinished() {}
    fun customWrapAdded(wrap: CustomWrap) {}
    fun customWrapRemoved(wrap: CustomWrap) {}
  }

  companion object {
    @JvmStatic
    fun isCustomWrapsSupportEnabled(): Boolean =
      Registry.`is`("editor.custom.soft.wraps.support.enabled") &&
      Registry.`is`("editor.use.new.soft.wraps.impl")
  }
}
