/*
 * A Jewel toolbar for an IntelliJ plugin tool window. Icons need dark-theme and HiDPI variants.
 * Reviewers: focus on how the icon variant files are referenced.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.PathIconKey

@Composable
private fun iconPath(base: String): String {
  // Pick the dark variant explicitly by theme, and always use the @2x variant for crispness.
  val themed = if (JewelTheme.isDark) "${base}_dark" else base
  return "icons/$themed@2x.svg"
}

@Composable
fun EditorToolbar(onRefresh: () -> Unit, onFilter: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier) {
    IconButton(onClick = onRefresh) {
      Icon(key = PathIconKey(iconPath("refresh"), EditorToolbarIcons::class.java), contentDescription = "Refresh")
    }
    IconButton(onClick = onFilter) {
      Icon(key = PathIconKey(iconPath("filter"), EditorToolbarIcons::class.java), contentDescription = "Filter")
    }
  }
}

private object EditorToolbarIcons
