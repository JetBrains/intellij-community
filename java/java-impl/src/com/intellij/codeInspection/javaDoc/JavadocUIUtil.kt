// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Cell
import kotlin.reflect.KMutableProperty0

@Suppress("unused")
public object JavadocUIUtil {
  @Deprecated(message = "Will be removed. Inline if you need this functionality")
  public fun <T> Cell<ComboBox<T>>.bindItem(property: KMutableProperty0<T>): Cell<ComboBox<T>> = applyToComponent {
    selectedItem = property.get()
    addActionListener {
      @Suppress("UNCHECKED_CAST")
      property.set(selectedItem as T)
    }
  }
}
