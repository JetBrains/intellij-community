// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.icons.AllIcons
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.IconWithOverlay
import com.intellij.util.IconUtil
import java.awt.Shape
import java.awt.geom.Path2D
import javax.swing.Icon


class IconWithVirtualOverlay(main: Icon, overlay: Icon) : IconWithOverlay(main, overlay) {

  constructor(main: Icon) : this(main, AllIcons.Debugger.ThreadVirtual)

  override fun replaceBy(replacer: IconReplacer) = IconWithVirtualOverlay(
    replacer.replaceIcon(mainIcon),
  )

  override fun copy() = IconWithVirtualOverlay(mainIcon, overlayIcon)

  override fun deepCopy() = IconWithVirtualOverlay(
    IconUtil.copy(mainIcon, ancestor = null),
  )

  override fun getOverlayShape(x: Int, y: Int): Shape {
    val scale = getScale()

    val path: Path2D = Path2D.Double()
    path.moveTo(16.0 * scale, 16.0 * scale)
    path.lineTo(8.0 * scale, 16.0 * scale)
    path.lineTo(8.0 * scale, 10.0 * scale)
    path.quadTo(8.0 * scale, 8.0 * scale, 10.0 * scale, 8.0 * scale)
    path.lineTo(16.0 * scale, 8.0 * scale)
    path.closePath()

    return path;
  }

}
