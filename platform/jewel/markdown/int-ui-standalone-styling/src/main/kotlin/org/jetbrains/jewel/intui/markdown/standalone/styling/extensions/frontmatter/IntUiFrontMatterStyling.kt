// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.frontmatter

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterColors
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterMetrics
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterStyling

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun FrontMatterStyling.Companion.light(
    colors: FrontMatterColors = FrontMatterColors.light(),
    metrics: FrontMatterMetrics = FrontMatterMetrics.defaults(),
): FrontMatterStyling = FrontMatterStyling(colors, metrics)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun FrontMatterStyling.Companion.dark(
    colors: FrontMatterColors = FrontMatterColors.dark(),
    metrics: FrontMatterMetrics = FrontMatterMetrics.defaults(),
): FrontMatterStyling = FrontMatterStyling(colors, metrics)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun FrontMatterColors.Companion.light(
    borderColor: Color = Color(0xffd1d9e0),
    background: Color = Color.Unspecified,
): FrontMatterColors = FrontMatterColors(borderColor, background)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun FrontMatterColors.Companion.dark(
    borderColor: Color = Color(0xff3d444d),
    background: Color = Color.Unspecified,
): FrontMatterColors = FrontMatterColors(borderColor, background)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun FrontMatterMetrics.Companion.defaults(
    borderWidth: Dp = 1.dp,
    cellPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
): FrontMatterMetrics = FrontMatterMetrics(borderWidth, cellPadding)
