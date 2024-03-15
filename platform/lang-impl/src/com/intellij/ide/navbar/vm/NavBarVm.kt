// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

import com.intellij.ide.navbar.ide.NavBarVmItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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

  fun selectTail()

  fun showPopup()
}
