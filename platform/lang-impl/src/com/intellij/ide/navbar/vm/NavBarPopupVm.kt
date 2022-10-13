// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

internal interface NavBarPopupVm {

  val items: List<NavBarVmItem>

  val selectedChild: NavBarVmItem?

  fun popupResult(result: PopupResult)
}
