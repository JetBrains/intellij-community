// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

/**
 * @param T type of item, only required to have a presentation
 */
internal interface NavBarPopupVm<T : NavBarPopupItem> {

  val items: List<T>

  val initialSelectedItemIndex: Int

  fun itemsSelected(selectedItems: List<T>)

  fun cancel()

  fun complete()
}
