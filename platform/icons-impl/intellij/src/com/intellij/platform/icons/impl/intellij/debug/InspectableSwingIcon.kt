// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.debug

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.scale.IconScale
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * The state of a Swing icon of the icons API, for debugging tools such as the Icon Inspector.
 *
 * The Swing icon class itself is internal. Its public form would have two `getScale()` methods, and this breaks Java
 * callers. This interface has no `getScale()`.
 */
@ApiStatus.Internal
interface InspectableSwingIcon : Icon {
  /** The descriptor that the icon paints. */
  val iconDescriptor: IconDescriptor

  /** The scale of the icon, or `null` for no scale. */
  val iconScale: IconScale?

  /** The explicit dark state: `true` for the dark artwork, `false` for the light artwork, `null` to follow the theme. */
  val dark: Boolean?

  /** A Swing icon of [descriptor] with the same scale and dark state as this icon. */
  fun withIconDescriptor(descriptor: IconDescriptor): Icon
}
