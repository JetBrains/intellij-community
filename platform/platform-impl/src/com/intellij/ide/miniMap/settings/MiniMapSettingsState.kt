// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.settings

/**
 * @param filterType Filter type for downscale the image. Could affect both quality and performance.
 * @param enabled Enables MiniMap for selected filetypes.
 * @param resizable MiniMap resize is enabled.
 * @param width Default width
 * @param rightAligned If false, MiniMap will be on the left side
 * @param fileTypes List of file extensions for which we want to show MiniMap. For example txt;kt;java;zpln. By default, enabled only for
 * Zeppelin Scientific notebooks.
 */
data class MiniMapSettingsState(var filterType: FilterType = FilterType.Nearest,
                                var enabled: Boolean = true,
                                var resizable: Boolean = false,
                                var width: Int = 200,
                                var rightAligned: Boolean = true,
                                var fileTypes: List<String> = listOf("zpln"))