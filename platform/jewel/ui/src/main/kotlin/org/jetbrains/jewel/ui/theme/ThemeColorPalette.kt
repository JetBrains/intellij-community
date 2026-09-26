// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette

@OptIn(InternalJewelApi::class)
@Composable
private fun ThemeColorPalette.defaultTable(): ThemeColorPalette =
    when {
        isIslands && JewelTheme.isDark -> DefaultColorPalette.IslandsDark
        isIslands && !JewelTheme.isDark -> DefaultColorPalette.IslandsLight
        !isIslands && JewelTheme.isDark -> DefaultColorPalette.Dark
        else -> DefaultColorPalette.Light
    }

@Composable
private fun ThemeColorPalette.orDefault(index: Int, accessor: ThemeColorPalette.(Int) -> Color?): Color =
    accessor(index) ?: defaultTable().accessor(index) ?: Color.Unspecified

/**
 * Retrieves a gray color from the palette by its index, falling back to Jewel's built-in default palette if the current
 * Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.grayOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::grayOrNull)

/**
 * Retrieves a blue color from the palette by its index, falling back to Jewel's built-in default palette if the current
 * Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.blueOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::blueOrNull)

/**
 * Retrieves a green color from the palette by its index, falling back to Jewel's built-in default palette if the
 * current Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.greenOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::greenOrNull)

/**
 * Retrieves a red color from the palette by its index, falling back to Jewel's built-in default palette if the current
 * Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.redOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::redOrNull)

/**
 * Retrieves a yellow color from the palette by its index, falling back to Jewel's built-in default palette if the
 * current Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.yellowOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::yellowOrNull)

/**
 * Retrieves an orange color from the palette by its index, falling back to Jewel's built-in default palette if the
 * current Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.orangeOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::orangeOrNull)

/**
 * Retrieves a purple color from the palette by its index, falling back to Jewel's built-in default palette if the
 * current Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.purpleOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::purpleOrNull)

/**
 * Retrieves a teal color from the palette by its index, falling back to Jewel's built-in default palette if the current
 * Look and Feel does not provide a color for the requested index.
 *
 * This spares callers from having to keep their own copy of the default palette values around just to have a sensible
 * fallback for incomplete or missing LaF palettes (e.g. most 3rd-party themes).
 *
 * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
 * Feel. Some LaFs may only have a partial palette, or none at all.
 *
 * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and above
 *   are valid.
 * @return The [Color] at the specified index from this palette if available, otherwise the corresponding color from
 *   Jewel's default palette, or [Color.Unspecified] if the index is out of bounds even there.
 */
@Composable
public fun ThemeColorPalette.tealOrDefault(index: Int): Color = orDefault(index, ThemeColorPalette::tealOrNull)
