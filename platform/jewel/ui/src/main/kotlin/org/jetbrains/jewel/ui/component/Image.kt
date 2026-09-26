// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.newUiChecker
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

/**
 * A Composable that lays out and draws a given [IconKey].
 *
 * This component is similar to [Icon], but it does not have a default size, and it exposes more control over the
 * image's appearance, like [contentScale], [alignment], and [alpha]. This will attempt to size the composable according
 * to the [IconKey]'s intrinsic size. However, an optional [Modifier] can be provided to adjust the sizing or draw
 * additional content (e.g., a background).
 *
 * @param iconKey The [IconKey] to draw.
 * @param contentDescription Text used by accessibility services to describe what this image represents. This should
 *   always be provided unless this image is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take. This text should be localized, such as by using `stringResource` or similar.
 * @param modifier [Modifier] for this image.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `iconKey.iconClass`.
 * @param alignment Alignment parameter used to place the [iconKey] in the given bounds.
 * @param contentScale Scale parameter used to determine the aspect ratio scaling to be used if the bounds are a
 *   different size from the intrinsic size of the [iconKey].
 * @param alpha Opacity to be applied to the [iconKey] when it is rendered on screen.
 * @param colorFilter [ColorFilter] to apply for the [iconKey] when it is rendered onscreen.
 * @see Icon
 */
@Composable
public fun Image(
    iconKey: IconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = iconKey.iconClass,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
) {
    Image(
        iconKey,
        contentDescription,
        hints = emptyArray(),
        modifier,
        iconClass,
        alignment,
        contentScale,
        alpha,
        colorFilter,
    )
}

/**
 * A Composable that lays out and draws a given [IconKey].
 *
 * This component is similar to [Icon], but it does not have a default size, and it exposes more control over the
 * image's appearance, like [contentScale], [alignment], and [alpha]. This will attempt to size the composable according
 * to the [IconKey]'s intrinsic size. However, an optional [Modifier] can be provided to adjust the sizing or draw
 * additional content (e.g., a background).
 *
 * @param iconKey The [IconKey] to draw.
 * @param contentDescription Text used by accessibility services to describe what this image represents. This should
 *   always be provided unless this image is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take. This text should be localized, such as by using `stringResource` or similar.
 * @param hints [PainterHint]s to be used by the painter.
 * @param modifier [Modifier] for this image.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `iconKey.iconClass`.
 * @param alignment Alignment parameter used to place the [iconKey] in the given bounds.
 * @param contentScale Scale parameter used to determine the aspect ratio scaling to be used if the bounds are a
 *   different size from the intrinsic size of the [iconKey].
 * @param alpha Opacity to be applied to the [iconKey] when it is rendered on screen.
 * @param colorFilter [ColorFilter] to apply for the [iconKey] when it is rendered onscreen.
 * @see Icon
 */
@Composable
public fun Image(
    iconKey: IconKey,
    contentDescription: String?,
    vararg hints: PainterHint,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = iconKey.iconClass,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
) {
    val isNewUi = JewelTheme.newUiChecker.isNewUi()
    val path = remember(iconKey, isNewUi) { iconKey.path(isNewUi) }
    val painterProvider = rememberResourcePainterProvider(path, iconClass)
    val painter by painterProvider.getPainter(*hints)

    Image(painter, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)
}
