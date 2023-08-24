// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.ide.NavBarVmItem.ItemExpandResult
import com.intellij.ide.navbar.impl.children
import com.intellij.ide.navbar.impl.isModuleContentRoot
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock

internal class IdeNavBarVmItem @RequiresReadLock constructor(
  item: NavBarItem,
) : NavBarVmItem {

  init {
    ThreadingAssertions.assertReadAccess()
  }

  override val pointer: Pointer<out NavBarItem> = item.createPointer()

  override val presentation: NavBarItemPresentation = item.presentation()

  override val isModuleContentRoot: Boolean = item.isModuleContentRoot()

  override suspend fun children(): List<NavBarVmItem>? {
    return fetch {
      childItems()
    }
  }

  override suspend fun expand(): ItemExpandResult? {
    return fetch {
      ItemExpandResult(childItems(), navigateOnClick())
    }
  }

  private fun NavBarItem.childItems(): List<NavBarVmItem> {
    return children().toVmItems()
  }

  // Synthetic string field for fast equality heuristics
  // Used to match element's direct child in the navbar with the same child in its popup
  private val texts = item.javaClass.canonicalName + "$" +
                      presentation.text.replace("$", "$$") + "$" +
                      presentation.popupText?.replace("$", "$$")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as IdeNavBarVmItem
    return texts == other.texts
  }

  override fun hashCode(): Int {
    return texts.hashCode()
  }

  override fun toString(): String {
    return texts
  }
}

private suspend fun <T> NavBarVmItem.fetch(selector: NavBarItem.() -> T): T? {
  return readAction {
    pointer.dereference()?.selector()
  }
}

internal fun List<NavBarItem>.toVmItems(): List<NavBarVmItem> {
  return map {
    IdeNavBarVmItem(it)
  }
}
