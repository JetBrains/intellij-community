/*
 * A custom checkbox for a Jewel-based IntelliJ plugin that renders its own stateful SVG icons.
 * The resources/icons/ directory ships: checkBoxSelected.svg, checkBoxSelected_dark.svg,
 * checkBoxDisabled.svg, checkBoxDisabled_dark.svg -- but NO base checkBox.svg.
 * Reviewers: focus on icon resolution and fallback.
 */
package com.example.plugin.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.PathIconKey

private object CheckBoxIcons

@Composable
fun CustomCheckbox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val path =
    when {
      !enabled -> "icons/checkBoxDisabled.svg"
      checked -> "icons/checkBoxSelected.svg"
      // Unchecked+enabled: expects the loader to fall back to a base icon...
      else -> "icons/checkBox.svg"
    }

  Icon(
    key = PathIconKey(path, CheckBoxIcons::class.java),
    contentDescription = if (checked) "Checked" else "Unchecked",
    modifier = modifier.clickable(enabled = enabled, onClick = onToggle),
  )
}
