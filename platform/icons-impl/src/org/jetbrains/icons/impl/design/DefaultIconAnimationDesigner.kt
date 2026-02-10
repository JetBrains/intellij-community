// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.design

import org.jetbrains.icons.design.IconAnimationDesigner
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.impl.rendering.IconAnimationFrame

class DefaultIconAnimationDesigner(
  val rootDesigner: DefaultIconDesigner
): IconAnimationDesigner {
  private val frames = mutableListOf<IconAnimationFrame>()

  override fun frame(duration: Long, builder: IconDesigner.() -> Unit) {
    val designer = rootDesigner.createNestedDesigner()
    designer.builder()
    frames.add(IconAnimationFrame(designer.buildLayers(), duration))
  }

  fun build(): List<IconAnimationFrame> {
    return frames
  }
}