// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.icons.IconWithRectangularOverlay
import com.intellij.ui.icons.PredefinedIconOverlayService
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Rectangle
import javax.swing.Icon

@Internal
class PredefinedIconOverlayServiceImpl : PredefinedIconOverlayService {

  override fun createSymlinkIcon(icon: Icon): Icon =
    if (isNewUI()) {
      IconWithRectangularOverlay(
        icon,
        PlatformIcons.SYMLINK_ICON,
        Rectangle(16 - 7, 0, 7, 7)
      )
    }
    else {
      LayeredIcon.create(icon, PlatformIcons.SYMLINK_ICON)
    }
}
