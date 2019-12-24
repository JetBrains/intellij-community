// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import javax.swing.JComponent

data class RegisterToolWindowTask @JvmOverloads constructor(
  val id: String,
  val anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
  val component: JComponent? = null,
  val sideTool: Boolean = false,
  val canCloseContent: Boolean = true,
  val canWorkInDumbMode: Boolean = true,
  val shouldBeAvailable: Boolean = true
) {
  companion object {
    @JvmStatic
    @JvmOverloads
    fun notClosable(id: String, anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM) = RegisterToolWindowTask(id, canCloseContent = false, anchor = anchor)

    @JvmStatic
    @JvmOverloads
    fun closable(id: String, anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM) = RegisterToolWindowTask(id, anchor = anchor)
  }
}