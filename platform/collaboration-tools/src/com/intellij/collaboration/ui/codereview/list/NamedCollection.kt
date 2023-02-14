// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import org.jetbrains.annotations.Nls

data class NamedCollection<T> private constructor(val namePlural: @Nls String, val items: List<T>) {
  companion object {
    fun <T> create(namePlural: @Nls String, items: List<T>): NamedCollection<T>? =
      if (items.isEmpty()) {
        null
      }
      else {
        NamedCollection(namePlural, items)
      }
  }
}