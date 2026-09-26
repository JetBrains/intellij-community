// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus.Internal

interface NavBarVmItem {

  @Internal
  companion object {
    @Internal // IJPL-149893
    @JvmField
    val SELECTED_ITEMS: DataKey<List<NavBarVmItem>> = DataKey.create("nav.bar.selection")
  }

  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int

  val presentation: NavBarItemPresentation

  suspend fun children(): List<NavBarVmItem>?

  suspend fun expand(): NavBarItemExpandResult? {
    return children()?.let {
      NavBarItemExpandResult(children = it, navigateOnClick = false)
    }
  }
}
