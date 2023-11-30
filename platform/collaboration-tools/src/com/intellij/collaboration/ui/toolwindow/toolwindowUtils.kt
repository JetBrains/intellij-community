// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx

fun ToolWindow.dontHideOnEmptyContent() {
  setToHideOnEmptyContent(false)
  (this as? ToolWindowEx)?.emptyText?.text = ""
}