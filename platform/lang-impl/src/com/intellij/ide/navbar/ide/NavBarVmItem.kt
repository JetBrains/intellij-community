// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.impl.isModuleContentRoot
import com.intellij.ide.navbar.vm.NavBarPopupItem
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction

internal class NavBarVmItem(
  val pointer: Pointer<out NavBarItem>,
  override val presentation: NavBarItemPresentation,
  val isModuleContentRoot: Boolean,
  itemClass: Class<NavBarItem>,
) : NavBarPopupItem {

  // Synthetic string field for fast equality heuristics
  // Used to match element's direct child in the navbar with the same child in its popup
  private val texts = itemClass.canonicalName + "$" +
                      presentation.text.replace("$", "$$") + "$" +
                      presentation.popupText?.replace("$", "$$")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as NavBarVmItem
    return texts == other.texts
  }

  override fun hashCode(): Int {
    return texts.hashCode()
  }

  override fun toString(): String {
    return texts
  }
}

internal suspend fun <T> NavBarVmItem.fetch(selector: NavBarItem.() -> T): T? {
  return readAction {
    pointer.dereference()?.selector()
  }
}

internal fun List<NavBarItem>.toVmItems(): List<NavBarVmItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return map {
    NavBarVmItem(it.createPointer(), it.presentation(), it.isModuleContentRoot(), it.javaClass)
  }
}
