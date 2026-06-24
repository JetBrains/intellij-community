/*
 * A reusable status badge for a Jewel-based IntelliJ plugin. The author's intent is that it be
 * portable: rendered in the plugin tool window (bridge), and also in @Preview and standalone UI
 * unit tests. Their strategy is "read the LaF key with a hardcoded Int UI fallback" so that in the
 * bridge the IDE value wins and standalone falls back to the intended default.
 *
 * Reviewers: focus on whether this read-with-fallback strategy actually holds for BOTH keys.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier) {
  val isDark = JewelTheme.isDark

  // (a) A plugin-owned custom key. No plain Swing LaF defines this, so standalone the read misses
  //     and drops to the fallback -- the intended Int UI value.
  val badgeBg =
    retrieveColor(
      key = "MyPlugin.Badge.background",
      isDark = isDark,
      default = Color(0xFFDFF0D8),
      defaultDark = Color(0xFF2B3B2B),
    )

  // (b) The badge text color. Author reuses a standard Swing key with the same trick, expecting the
  //     fallback to apply standalone just like (a).
  val badgeFg =
    retrieveColor(
      key = "Label.foreground",
      isDark = isDark,
      default = Color(0xFF1A1A1A),
      defaultDark = Color(0xFFBBBBBB),
    )

  Text(text = label, color = badgeFg, modifier = modifier.background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp))
}
