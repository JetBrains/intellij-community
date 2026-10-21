// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.OnOffButtonIcons
import org.jetbrains.jewel.ui.component.styling.OnOffButtonMetrics
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

/**
 * Creates an Int UI light [OnOffButtonStyle] with the provided parameters.
 *
 * @param metrics The size metrics for the on/off button.
 * @param icons The icon keys for the on/off button.
 */
public fun OnOffButtonStyle.Companion.light(
    metrics: OnOffButtonMetrics = OnOffButtonMetrics.default(),
    icons: OnOffButtonIcons = OnOffButtonIcons.default(),
): OnOffButtonStyle = OnOffButtonStyle(metrics, icons)

/**
 * Creates an Int UI dark [OnOffButtonStyle] with the provided parameters.
 *
 * @param metrics The size metrics for the on/off button.
 * @param icons The icon keys for the on/off button.
 */
public fun OnOffButtonStyle.Companion.dark(
    metrics: OnOffButtonMetrics = OnOffButtonMetrics.default(),
    icons: OnOffButtonIcons = OnOffButtonIcons.default(),
): OnOffButtonStyle = OnOffButtonStyle(metrics, icons)

/**
 * Creates [OnOffButtonMetrics] with the size of the IntelliJ Platform `toggle*.svg` images.
 *
 * @param size The size of the on/off button, focus ring area included.
 */
public fun OnOffButtonMetrics.Companion.default(size: DpSize = DpSize(32.dp, 22.dp)): OnOffButtonMetrics =
    OnOffButtonMetrics(size)

/**
 * Creates [OnOffButtonIcons] that use the IntelliJ Platform `toggle*.svg` images. The light and dark themes share the
 * images, because the theme's `toggle-*` colors recolor them.
 *
 * @param onOffButton The base icon key for the on/off button images.
 */
public fun OnOffButtonIcons.Companion.default(
    onOffButton: IconKey =
        PathIconKey(path = "com/intellij/ide/ui/laf/icons/toggle.svg", iconClass = OnOffButtonIcons::class.java)
): OnOffButtonIcons = OnOffButtonIcons(onOffButton)
