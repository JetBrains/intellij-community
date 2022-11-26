// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.actions

import com.intellij.ide.navbar.vm.NavBarVm

internal abstract class NavBarActionHandlerImpl(private val vm: NavBarVm) : NavBarActionHandler {

  override fun moveHome() {
    vm.shiftSelectionTo(NavBarVm.SelectionShift.FIRST)
  }

  override fun moveLeft() {
    vm.shiftSelectionTo(NavBarVm.SelectionShift.PREV)
  }

  override fun moveRight() {
    vm.shiftSelectionTo(NavBarVm.SelectionShift.NEXT)
  }

  override fun moveEnd() {
    vm.shiftSelectionTo(NavBarVm.SelectionShift.LAST)
  }

  override fun moveUpDown() {
    vm.showPopup()
  }

  override fun escape() {
    // v2 escape is handled by the popup or by the global action which brings the focus to the editor
    error("unsupported in v2")
  }

  override fun enter() {
    vm.popup.value?.complete() ?: vm.showPopup()
  }

  override fun navigate() {
    // v2 navigation is handled by the standard action which gets Navigatable[] from DataContext
    error("unsupported in v2")
  }
}
