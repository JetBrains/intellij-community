// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object EmptyCustomWrapModel : CustomWrapModel {
  override fun getWrapsAtOffset(offset: Int): List<CustomWrap> {
    return emptyList()
  }

  override fun getWrapsInRange(startOffset: Int, endOffset: Int): List<CustomWrap> {
    return emptyList()
  }

  override fun addListener(listener: CustomWrapModel.Listener, disposable: Disposable) {
  }

  override fun hasWraps(): Boolean {
    return false
  }

  override fun addWrap(offset: Int, indentInColumns: Int, priority: Int): CustomWrap? {
    return null
  }

  override fun getWraps(): List<CustomWrap> {
    return emptyList()
  }

  override fun removeWrap(wrap: CustomWrap) {
  }
}