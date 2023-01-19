// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

import com.intellij.ide.navbar.NavBarItemPresentation
import kotlinx.coroutines.flow.StateFlow

interface NavBarItemVm {

  val presentation: NavBarItemPresentation

  val isModuleContentRoot: Boolean

  val isFirst: Boolean

  val isLast: Boolean

  val selected: StateFlow<Boolean>

  fun isNextSelected(): Boolean

  fun isInactive(): Boolean

  fun select()

  fun showPopup()

  fun activate()
}
