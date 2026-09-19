/*
 * A Jewel toolbar for an IntelliJ plugin that loads icons by base name and ships the correct
 * variant files beside them.
 *
 * This file is intended to be CORRECT and is a PRECISION case. resources/icons/ contains:
 *   action.svg          (base, 16x16 intrinsic size)
 *   action_dark.svg     (dark variant)
 *   action@2x.png        (HiDPI raster PNG alternative)
 *   action@2x_dark.png   (HiDPI dark raster)
 * There is intentionally NO action@2x.svg, because the Jewel render path does not look up @2x for SVG.
 *
 * A good review should NOT flag the @2x.png, the absent @2x.svg, or the base-name reference; it should
 * recognize the variant setup as following Jewel's filename conventions.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.icon.PathIconKey

private object ToolbarIcons

@Composable
fun CorrectToolbar(onAction: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier) {
    IconButton(onClick = onAction) {
      // Reference only the base name; the PainterHint pipeline selects _dark / @2x variants automatically.
      Icon(key = PathIconKey("icons/action.svg", ToolbarIcons::class.java), contentDescription = "Run action")
    }
  }
}
