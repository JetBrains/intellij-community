// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.darkState
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext

/**
 * The rendering context for icons drawn in a Jewel theme whose dark state is [isDark].
 *
 * If the theme of the icon renderer manager has the same dark state, the icons follow that theme. This keeps the icons
 * of the IDE bridge the same as the Swing icons around them. If the dark states differ, [isDark] is an explicit dark
 * state of the context. A dark state set inside an icon descriptor still wins over it. A theme that does not supply a
 * dark state (standalone Jewel) uses [isDark].
 */
internal fun createIconRenderingContext(updateFlow: MutableIconUpdateFlow, isDark: Boolean): RenderingContext {
    val following = IconRendererManager.createRenderingContext(updateFlow, DefaultImageModifiers(isDark = isDark))
    val themeDark = following.theme.darkState()
    if (themeDark == null || themeDark == isDark) return following
    return IconRendererManager.createRenderingContext(
        updateFlow,
        DefaultImageModifiers(isDark = isDark, darkOverride = isDark),
    )
}
