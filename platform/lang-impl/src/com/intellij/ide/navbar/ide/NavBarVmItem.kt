// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.openapi.actionSystem.DataKey

interface NavBarVmItem {

  companion object {
    @JvmField
    val SELECTED_ITEMS: DataKey<List<NavBarVmItem>> = DataKey.create("nav.bar.selection")
  }

  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int

  val presentation: NavBarItemPresentation

  val isModuleContentRoot: Boolean get() = false

  suspend fun children(): List<NavBarVmItem>?

  suspend fun expand(): ItemExpandResult? {
    return children()?.let {
      ItemExpandResult(children = it, navigateOnClick = false)
    }
  }

  data class ItemExpandResult(val children: List<NavBarVmItem>, val navigateOnClick: Boolean)
}
