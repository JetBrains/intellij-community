// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext

/**
 * The rendering context for icons drawn in a Jewel theme whose dark state is [isDark].
 *
 * If [followsIdeTheme] is `true`, the Jewel theme is the theme of the IDE (`SwingBridgeTheme`), so the icons follow the
 * theme of the icon renderer manager, as the Swing icons around them do. [isDark] is then only the fallback for a theme
 * that does not supply a dark state. Else, for example in a fixed `IntUiTheme(isDark = true)` or in standalone Jewel,
 * [isDark] is an explicit dark state of the context, which stays when the IDE theme changes. A dark state set inside an
 * icon descriptor still wins over it.
 */
internal fun createIconRenderingContext(
    updateFlow: MutableIconUpdateFlow,
    isDark: Boolean,
    followsIdeTheme: Boolean,
): RenderingContext {
    val forcedDark = if (followsIdeTheme) null else isDark
    return IconRendererManager.createRenderingContext(
        updateFlow,
        DefaultImageModifiers(isDark = isDark, forcedDark = forcedDark),
    )
}
