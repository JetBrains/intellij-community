// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.ide.vm

import com.intellij.platform.navbar.NavBarItemPresentation
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
