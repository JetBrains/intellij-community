// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

const val WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID: String = "root"

interface WindowInfo {
  val id: String?

  val order: Int

  val weight: Float
  val sideWeight: Float

  val isVisible: Boolean

  @get:ApiStatus.Internal
  val isFromPersistentSettings: Boolean

  val anchor: ToolWindowAnchor

  val floatingBounds: Rectangle?

  val isMaximized: Boolean

  val isSplit: Boolean

  val type: ToolWindowType

  val internalType: ToolWindowType

  val isActiveOnStart: Boolean

  val isAutoHide: Boolean

  val isDocked: Boolean

  val isShowStripeButton: Boolean

  val contentUiType: ToolWindowContentUiType

  /**
   * The identifier of the `ToolWindowPane` that this tool window info belongs to
   *
   * The `WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID` identifier is used for the main frame. A null value should be considered the same as
   * thi default value
   */
  val toolWindowPaneId: String?
}

val WindowInfo.safeToolWindowPaneId: String
  get() = toolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID