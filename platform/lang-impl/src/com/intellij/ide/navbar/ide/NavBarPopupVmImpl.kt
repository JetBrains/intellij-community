// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.vm.NavBarPopupItem
import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CompletableDeferred

internal class NavBarPopupVmImpl(
  override val items: List<NavBarVmItem>,
  override val initialSelectedItemIndex: Int,
) : NavBarPopupVm {

  var selectedItems: List<NavBarVmItem> = if (initialSelectedItemIndex == -1) emptyList() else listOf(items[initialSelectedItemIndex])
    get() {
      EDT.assertIsEdt()
      return field
    }
    private set(value) {
      EDT.assertIsEdt()
      field = value
    }

  override fun itemsSelected(selectedItems: List<NavBarPopupItem>) {
    @Suppress("UNCHECKED_CAST")
    this.selectedItems = selectedItems as List<NavBarVmItem>
  }

  val result: CompletableDeferred<NavBarVmItem> = CompletableDeferred()

  override fun cancel() {
    result.cancel()
  }

  override fun complete() {
    selectedItems.firstOrNull()?.let {
      result.complete(it)
    }
  }
}
