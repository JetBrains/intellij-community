// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.legacyIconSupport.SwingIconManager
import javax.swing.Icon

class DefaultSwingIconManager: SwingIconManager {
  override fun toSwingIcon(icon: org.jetbrains.icons.Icon): Icon {
    return SwingIcon(icon)
  }
}
