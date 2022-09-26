// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock


/**
 * Extension point to fill navigation bar data structure. Connects an item with its parent and children.
 *
 * To find the current focused element provide a DataRule for <code>NAVBAR_ITEM_KEY</code>
 *
 * @see com.intellij.ide.navbar.NavBarItem.NAVBAR_ITEM_KEY
 */
interface NavBarItemProvider {

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NavBarItemProvider> = ExtensionPointName.create("com.intellij.navbar.item.provider")
  }

  /**
   * Finds a known parent of this item if any.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @JvmDefault
  fun findParent(item: NavBarItem): NavBarItem? = null

  /**
   * Lists known item's children if any.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @JvmDefault
  fun iterateChildren(item: NavBarItem): Iterable<NavBarItem> = emptyList()

}
