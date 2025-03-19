// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

/**
 * A toolbar, a popup, or another UI that shows actions on the screen.
 *
 * @see AnActionEvent.getUiKind
 */
interface ActionUiKind {

  interface Toolbar : ActionUiKind {
    fun isHorizontal(): Boolean = true
  }

  interface Popup : ActionUiKind {
    fun isMainMenu(): Boolean = false
    fun isSearchPopup(): Boolean = false
  }

  companion object {
    @JvmField
    val NONE = object : ActionUiKind {}

    @JvmField
    val TOOLBAR = object : Toolbar {}

    @JvmField
    val POPUP = object : Popup {}

    @JvmField
    val MAIN_MENU = object : Popup {
      override fun isMainMenu(): Boolean = true
    }

    @JvmField
    val SEARCH_POPUP = object : Popup {
      override fun isSearchPopup(): Boolean = true
    }
  }
}
