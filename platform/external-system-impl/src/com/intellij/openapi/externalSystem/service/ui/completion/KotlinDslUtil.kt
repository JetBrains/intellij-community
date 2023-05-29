// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Cell

fun <T, C : TextCompletionField<T>> Cell<C>.whenTextChangedFromUi(
  parentDisposable: Disposable? = null,
  listener: (String) -> Unit
): Cell<C> {
  return applyToComponent {
    whenTextChangedFromUi(parentDisposable, listener)
  }
}

fun <T, C : TextCompletionComboBox<T>> Cell<C>.whenItemChangedFromUi(
  parentDisposable: Disposable? = null,
  listener: (T) -> Unit
): Cell<C> {
  return applyToComponent {
    whenItemChangedFromUi(parentDisposable, listener)
  }
}