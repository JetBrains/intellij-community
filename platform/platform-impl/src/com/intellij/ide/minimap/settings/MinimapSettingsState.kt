// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.util.ui.JBUI

/**
 * @param enabled Enables Minimap globally.
 * @param width Fixed width (scaled).
 * @param rightAligned If false, Minimap will be on the left side.
 *
 * The set of file types that support the minimap is determined by the
 * [com.intellij.ide.minimap.model.MinimapFileSupportPolicy] extension point rather than
 * stored here. By default the minimap is shown for all non-binary text files.
 */
data class MinimapSettingsState(var enabled: Boolean = true,
                                var width: Int = FIXED_WIDTH,
                                var scaleMode: MinimapScaleMode = MinimapScaleMode.FILL,
                                var rightAligned: Boolean = true) {
  companion object {
    val FIXED_WIDTH: Int = JBUI.scale(160)
  }
}
