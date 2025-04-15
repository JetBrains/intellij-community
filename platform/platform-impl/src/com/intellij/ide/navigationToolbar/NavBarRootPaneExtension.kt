// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar

import com.intellij.ide.ui.UISettingsListener
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JPanel

@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to `com.intellij.platform.navbar.frontend.NavBarRootPaneExtension`", level = DeprecationLevel.ERROR)
class NavBarRootPaneExtension {

  // used externally
  @ApiStatus.ScheduledForRemoval
  @Deprecated(message = "Use `JPanel` instead", level = DeprecationLevel.ERROR)
  abstract class NavBarWrapperPanel(layout: LayoutManager?) : JPanel(layout), UISettingsListener {
    override fun getComponentGraphics(graphics: Graphics): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }
}
