// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

import kotlinx.coroutines.flow.StateFlow

internal interface StaticNavBarVm {

  /**
   * `null` means invisible
   */
  val vm: StateFlow<NavBarVm?>
}
