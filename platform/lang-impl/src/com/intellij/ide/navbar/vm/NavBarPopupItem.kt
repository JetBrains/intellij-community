// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

import com.intellij.ide.navbar.NavBarItemPresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NavBarPopupItem {

  val presentation: NavBarItemPresentation
}
