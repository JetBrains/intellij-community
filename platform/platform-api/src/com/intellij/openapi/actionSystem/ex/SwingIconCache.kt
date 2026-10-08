// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * This class holds the Swing icons of one icon descriptor. A component keeps one instance and calls [update] on each update.
 *
 * A Swing icon has no `equals`. A component that builds a new icon on every update fires property changes and repaints.
 * [update] keeps the same icons while the descriptor and the theme stay the same.
 *
 * The cache key is the descriptor and the theme. A theme change builds new icons, because an icon keeps the theme of its first paint.
 *
 * Use this class on the EDT only. [disabled] is built on first read, and it reads the theme at that time.
 * A theme change before the first read is harmless. The next [update] call sees the new theme and builds the icons again.
 */
@ApiStatus.Internal
class SwingIconCache {
  private var descriptor: IconDescriptor? = null
  private var bright = JBColor.isBright()
  private var disabledIcon: Icon? = null

  /** The Swing icon of the descriptor. It is `null` while the cache has no descriptor. */
  var icon: Icon? = null
    private set

  /** The disabled Swing icon of the descriptor. It is `null` while the cache has no descriptor. The cache builds it on first read. */
  val disabled: Icon?
    get() {
      val descriptor = descriptor ?: return null
      return disabledIcon ?: ActionIconDescriptors.disabled(descriptor).createSwingIcon().also { disabledIcon = it }
    }

  /**
   * Sets the descriptor of the cache.
   * The cache keeps the current icons when the descriptor is equal and the theme is the same.
   * Otherwise, it builds the icons again. A `null` descriptor clears the icons.
   */
  fun update(descriptor: IconDescriptor?) {
    val bright = JBColor.isBright()
    if (descriptor == this.descriptor && bright == this.bright) return
    this.descriptor = descriptor
    this.bright = bright
    icon = descriptor?.createSwingIcon()
    disabledIcon = null
  }
}
