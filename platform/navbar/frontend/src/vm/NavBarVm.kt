// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.vm

import com.intellij.platform.navbar.NavBarVmItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NavBarVm {

  val items: StateFlow<List<NavBarItemVm>>

  val selectedIndex: StateFlow<Int>

  val popup: StateFlow<NavBarPopupVm<*>?>

  val activationRequests: Flow<NavBarVmItem>

  fun selection(): List<NavBarVmItem>

  enum class SelectionShift {
    FIRST,
    PREV,
    NEXT,
    LAST,
    ;
  }

  fun shiftSelectionTo(shift: SelectionShift)

  fun selectTail(withPopupOpen: Boolean)

  fun showPopup()
}
