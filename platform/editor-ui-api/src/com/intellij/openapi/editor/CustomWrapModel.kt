// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * Model for managing custom (user-defined) soft wraps in an editor.
 * Custom wraps persist across automatic soft wrap recalculations.
 *
 * @see Editor.getCustomWrapModel
 */
@ApiStatus.Experimental
interface CustomWrapModel {
  fun addWrap(offset: Int, indentInColumns: Int, priority: Int = 0): CustomWrap?
  fun getWraps(): List<CustomWrap>
  fun getWrapsInRange(startOffset: Int, endOffset: Int): List<CustomWrap>
  fun getWrapsAtOffset(offset: Int): List<CustomWrap>
  fun hasWraps(): Boolean
  fun removeWrap(wrap: CustomWrap)

  fun addListener(listener: Listener, disposable: Disposable)

  interface Listener {
    fun customWrapAdded(wrap: CustomWrap) {}
    fun customWrapRemoved(wrap: CustomWrap) {}
  }
}

