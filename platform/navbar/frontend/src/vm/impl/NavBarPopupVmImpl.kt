// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.vm.impl

import com.intellij.platform.navbar.frontend.vm.NavBarPopupItem
import com.intellij.platform.navbar.frontend.vm.NavBarPopupVm
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CompletableDeferred

internal class NavBarPopupVmImpl<T : NavBarPopupItem>(
  override val items: List<T>,
  override val initialSelectedItemIndex: Int,
) : NavBarPopupVm<T> {

  var selectedItems: List<T> = if (initialSelectedItemIndex == -1) emptyList() else listOf(items[initialSelectedItemIndex])
    get() {
      EDT.assertIsEdt()
      return field
    }
    private set(value) {
      EDT.assertIsEdt()
      field = value
    }

  override fun itemsSelected(selectedItems: List<T>) {
    this.selectedItems = selectedItems
  }

  val result: CompletableDeferred<T> = CompletableDeferred()

  override fun cancel() {
    result.cancel()
  }

  override fun complete() {
    selectedItems.firstOrNull()?.let {
      result.complete(it)
    }
  }
}
