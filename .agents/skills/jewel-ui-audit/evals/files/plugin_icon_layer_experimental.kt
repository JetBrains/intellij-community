/*
 * A composite plugin icon built via the experimental com.intellij.platform.icons framework, used in
 * shipping Jewel UI. Reviewers: focus on the API dependency's stability for a shipping plugin.
 */
package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.Shape
import org.jetbrains.jewel.ui.component.Icon

private object PluginIcons

@Composable
fun BadgedPluginIcon(accentColor: Color, modifier: Modifier = Modifier) {
  // Build an icon out of a base image plus a badge, using the experimental designer API directly.
  val designed =
    IconManager.getInstance().design {
      image("icons/base.svg", PluginIcons::class.java)
      badge { shape(accentColor, Shape.Circle) }
    }

  Icon(icon = designed, contentDescription = "Plugin", modifier = modifier)
}
