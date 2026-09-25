/*
 * A theming container + a card for a hypothetical Jewel-on-IntelliJ-bridge plugin tool window.
 * This file is intended to be largely CORRECT and is a PRECISION case: a good review should
 * recognize the layered-fallback + light/dark + theming-container pattern as right, and NOT
 * invent hardcoded-color or missing-fallback findings here.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Central theming container: one getter per styled value, each with a layered fallback and light/dark split. */
object SamplePanelTheme {
  val cardBackground: Color
    @Composable
    get() = rememberColor(
      key = "SamplePanel.Card.background",
      darkFallbackKey = "ColorPalette.Gray2",
      darkDefault = Color(0xFF2B2D30),
      lightFallbackKey = "ColorPalette.Gray13",
      lightDefault = Color(0xFFF7F8FA),
    )

  val cardBorderWidth
    @Composable
    get() = remember(JewelTheme.name) {
      retrieveIntAsDpOrUnspecified("SamplePanel.Card.borderWidth").takeOrElse { 1.dp }
    }
}

@Composable
private fun rememberColor(
  key: String,
  darkFallbackKey: String,
  darkDefault: Color,
  lightFallbackKey: String,
  lightDefault: Color,
): Color {
  val isDark = JewelTheme.isDark
  return remember(JewelTheme.name, isDark) {
    if (isDark) retrieveColorOrUnspecified(key).takeOrElse {
      retrieveColorOrUnspecified(darkFallbackKey).takeOrElse { darkDefault }
    } else retrieveColorOrUnspecified(key).takeOrElse {
      retrieveColorOrUnspecified(lightFallbackKey).takeOrElse { lightDefault }
    }
  }
}

@Composable
fun SampleCard(text: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .background(SamplePanelTheme.cardBackground, RoundedCornerShape(8.dp))
      .padding(12.dp),
  ) {
    Text(text)
  }
}
