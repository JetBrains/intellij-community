// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.vm

/**
 * @param T type of item, only required to have a presentation
 */
interface NavBarPopupVm<T : NavBarPopupItem> {

  val items: List<T>

  val initialSelectedItemIndex: Int

  fun itemsSelected(selectedItems: List<T>)

  fun cancel()

  fun complete()
}
