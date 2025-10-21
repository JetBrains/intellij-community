// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CustomToolLanguageConfigurable<T> {
  fun setIntrospectable(introspectable: LanguageRuntimeType.Introspectable)
  fun registerStateChangedCallback(stateChangedCallback: () -> Unit): Unit = Unit

  /**
   * Call [validate] first to make sure there are no errors
   */
  @RequiresEdt
  fun createCustomTool(): T?

  fun validate(): Collection<ValidationInfo>
}