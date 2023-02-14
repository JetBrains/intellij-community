// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

import com.intellij.ide.navbar.NavBarItem
import com.intellij.model.Pointer
import kotlinx.coroutines.flow.StateFlow

internal interface NavBarVm {

  val items: StateFlow<List<NavBarItemVm>>

  val selectedIndex: StateFlow<Int>

  val popup: StateFlow<NavBarPopupVm?>

  fun selection(): List<Pointer<out NavBarItem>>

  enum class SelectionShift {
    FIRST,
    PREV,
    NEXT,
    LAST,
    ;
  }

  fun shiftSelectionTo(shift: SelectionShift)

  fun selectTail()

  fun showPopup()
}
