// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend

import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.navbar.NavBarItemPresentation
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal


/**
 * An abstraction to be presented within a navigation bar.
 */
interface NavBarItem {

  companion object {
    @JvmField
    val NAVBAR_ITEM_KEY: DataKey<Pointer<out NavBarItem>> = DataKey.create("navigationBarItem")
  }

  /**
   * Creates a pointer to weakly store this item in UI components.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun createPointer(): Pointer<out NavBarItem>

  /**
   * Precalculates the presentation aspects of this item.
   *
   * @see NavBarItemPresentation
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun presentation(): NavBarItemPresentation

  /**
   * Returns a <code>NavigateRequest</code> for this item if it represents a navigatable
   * entity and <code>null</code> otherwise.
   *
   * @see com.intellij.pom.Navigatable
   * @see com.intellij.navigation.NavigationRequests
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun navigationRequest(): NavigationRequest? = null

  /**
   * Indicates whether navigation should be performed when item is selected from the popup menu.
   * The default behaviour is <code>false</code>, i.e. to show next popup with selected item's children.
   */
  fun navigateOnClick(): Boolean = false

  /**
   * Returns weight for this item for sorting when it is presented in navbar children popup
   */
  @Internal
  fun weight(): Int = Int.MAX_VALUE
}
