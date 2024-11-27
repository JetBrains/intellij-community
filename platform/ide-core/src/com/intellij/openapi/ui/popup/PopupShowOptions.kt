// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PopupOptions")
package com.intellij.openapi.ui.popup

import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Point

sealed interface PopupShowOptions

@ApiStatus.Internal
class PopupShowOptionsBuilder : PopupShowOptions {
  private var owner: Component? = null
  private var screenPoint: Point? = null
  private var considerForcedXY: Boolean = false
  val screenX: Int get() = screenPoint?.x ?: -1
  val screenY: Int get() = screenPoint?.y ?: -1

  fun withOwner(owner: Component): PopupShowOptionsBuilder = apply {
    this.owner = owner
  }

  fun withScreenXY(screenX: Int, screenY: Int): PopupShowOptionsBuilder = apply {
    this.screenPoint = if (screenX == -1 && screenY == -1) null else Point(screenX, screenY)
  }

  fun withForcedXY(considerForcedXY: Boolean): PopupShowOptionsBuilder = apply {
    this.considerForcedXY = considerForcedXY
  }

  fun build(): PopupShowOptionsImpl {
    val owner = owner
    checkNotNull(owner) { "Owner is not set" }
    return PopupShowOptionsImpl(owner, screenPoint, considerForcedXY)
  }
}

@ApiStatus.Internal
data class PopupShowOptionsImpl(
  val owner: Component,
  val screenPoint: Point?,
  val considerForcedXY: Boolean,
) {
  val screenX: Int get() = screenPoint?.x ?: -1
  val screenY: Int get() = screenPoint?.y ?: -1
}
