// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.vm

internal sealed interface PopupResult {
  object PopupResultLeft : PopupResult
  object PopupResultRight : PopupResult
  object PopupResultCancel : PopupResult
  class PopupResultSelect(val item: NavBarVmItem) : PopupResult
}
