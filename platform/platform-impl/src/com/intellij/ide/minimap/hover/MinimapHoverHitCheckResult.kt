// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.render.MinimapRenderEntry
import java.awt.Rectangle
import javax.swing.Icon

data class MinimapHoverHitCheckResult(
  val entry: MinimapRenderEntry,
  val rect: Rectangle,
  val text: String?,
  val icon: Icon?
)
