// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.platform.backend.presentation.TargetPresentation


internal class SeveralItemsWithPresentation(item: Any, presentation: TargetPresentation) : ItemWithPresentation(item, presentation) {

  private val allItems: MutableSet<Any> = mutableSetOf(item)

  fun addItem(item: Any) {
    allItems.add(item)
  }

}