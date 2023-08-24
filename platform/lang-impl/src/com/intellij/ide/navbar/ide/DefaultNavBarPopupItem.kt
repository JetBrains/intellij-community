// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.vm.NavBarPopupItem

internal class DefaultNavBarPopupItem(val item: NavBarVmItem) : NavBarPopupItem {

  override val presentation: NavBarItemPresentation
    get() = item.presentation
}
