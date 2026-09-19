/*
 * A small helper that resolves the search-match highlight color for a Jewel-on-bridge plugin panel.
 * Reviewers: focus on theme-change correctness of the cached read.
 */
package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Resolves the highlight color once, keyed on the theme name. */
@Composable
fun rememberSearchHighlightColor(): Color =
  remember(JewelTheme.name) {
    retrieveColorOrUnspecified("SearchMatch.background").takeOrElse { Color(0x33FF0000) }
  }
