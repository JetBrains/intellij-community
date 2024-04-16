// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.vm.impl

import com.intellij.platform.navbar.NavBarItemPresentation
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.vm.NavBarPopupItem

internal class DefaultNavBarPopupItem(val item: NavBarVmItem) : NavBarPopupItem {

  override val presentation: NavBarItemPresentation
    get() = item.presentation
}
