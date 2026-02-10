// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.legacyIconSupport

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconManager
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.icon
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.modifiers.fillMaxSize

/**
 * Layer for embedding swing icons.
 */
@ExperimentalIconsApi
fun IconDesigner.swingIcon(legacyIcon: javax.swing.Icon, modifier: IconModifier = IconModifier) {
  custom(SwingIconLayer(legacyIcon, modifier))
}

/**
 * Shorthand for creating a new icon from swing icon, uses IconDesigner.swingIcon().
 */
@ExperimentalIconsApi
fun javax.swing.Icon.toNewIcon(modifier: IconModifier = IconModifier): Icon {
  return icon {
    swingIcon(this@toNewIcon, modifier)
  }
}

/**
 * Converts specific Icon to swing Icon.
 * ! This is an expensive operation and can include image loading, reuse the instance if possible. !
 */
@ExperimentalIconsApi
fun Icon.toSwingIcon(): javax.swing.Icon {
  return swingIconManager().toSwingIcon(this)
}

@ExperimentalIconsApi
private fun swingIconManager(): SwingIconManager {
  return IconManager.getInstance() as? SwingIconManager ?: error("Current IconManager doesn't implement SwingIconManager")
}