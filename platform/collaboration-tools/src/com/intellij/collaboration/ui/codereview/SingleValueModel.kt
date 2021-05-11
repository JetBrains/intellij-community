// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

interface SingleValueModel<T> {
  var value: T

  fun addListener(listener: (newValue: T) -> Unit)

  fun addInvokeListener(listener: (newValue: T) -> Unit) {
    addListener(listener)
    listener(value)
  }
}