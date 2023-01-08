// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navbar.vm.PopupResult
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume

internal class NavBarPopupVmImpl(
  override val items: List<NavBarVmItem>,
  override val selectedChild: NavBarVmItem?,
  private val continuation: CancellableContinuation<PopupResult>
) : NavBarPopupVm {

  override fun popupResult(result: PopupResult) {
    if (continuation.isActive) {
      continuation.resume(result)
    }
  }
}
