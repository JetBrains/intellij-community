// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.actions

import com.intellij.platform.navbar.frontend.vm.NavBarVm

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

  override fun enter() {
    vm.popup.value?.complete() ?: vm.showPopup()
  }
}
