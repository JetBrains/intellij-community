// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

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
}
