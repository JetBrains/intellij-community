// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal


/**
 * Extension point to fill navigation bar data structure. Connects an item with its parent and children.
 *
 * To find the current focused element provide a [data rule][com.intellij.ide.impl.dataRules.GetDataRule] for [NavBarItem.NAVBAR_ITEM_KEY].
 */
interface NavBarItemProvider {

  @Internal
  companion object {
    @Internal
    @JvmField
    val EP_NAME: ExtensionPointName<NavBarItemProvider> = ExtensionPointName.create("com.intellij.navbar.item.provider")
  }

  /**
   * Finds a known parent of this item if any.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun findParent(item: NavBarItem): NavBarItem? = null

  /**
   * Lists known item's children if any.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun iterateChildren(item: NavBarItem): Iterable<NavBarItem> = emptyList()

}
