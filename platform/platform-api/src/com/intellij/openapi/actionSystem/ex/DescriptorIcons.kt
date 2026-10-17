// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * The Swing icons of one icon descriptor, kept between updates.
 *
 * A Swing icon has no `equals`. A component that builds a new icon on every update fires property changes and repaints.
 * So the component keeps one instance and asks [reuseOrBuild] for the next one.
 *
 * The cache key is the descriptor plus the theme. A theme change builds a new instance, because an icon keeps the theme of its first paint.
 *
 * Use this class on the EDT only. [disabled] is built on first read, and it reads the theme at that time.
 * A theme change before the first read is harmless. The next [reuseOrBuild] call sees the new theme and builds again.
 */
@ApiStatus.Internal
class DescriptorIcons private constructor(private val descriptor: IconDescriptor, private val bright: Boolean) {
  val icon: Icon = descriptor.createSwingIcon()
  val disabled: Icon by lazy {
    ActionIconDescriptors.disabled(descriptor).createSwingIcon()
  }

  companion object {
    /**
     * Returns [old] when it was built from an equal descriptor in the same theme. Otherwise builds new icons.
     * The caller keeps the result in a field.
     */
    @JvmStatic fun reuseOrBuild(descriptor: IconDescriptor, old: DescriptorIcons?): DescriptorIcons {
      val bright = JBColor.isBright()
      if (old != null && old.descriptor == descriptor && old.bright == bright) {
        return old
      }
      return DescriptorIcons(descriptor, bright)
    }

    /** The same as [reuseOrBuild], but a `null` descriptor gives `null`. */
    @JvmStatic fun reuseOrNull(descriptor: IconDescriptor?, old: DescriptorIcons?): DescriptorIcons? {
      if (descriptor == null) return null
      return reuseOrBuild(descriptor, old)
    }
  }
}
