package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.JewelTheme

val LocalProject = staticCompositionLocalOf<Project?> { throw AssertionError("No project provided") }

@Composable
fun ProjectBuildHeader(project: Project, modifier: Modifier = Modifier) {
  val currentProject = LocalProject.current
  val projectName = currentProject?.name ?: project.name
  Text("Active Project: $projectName", modifier = modifier)
  Icon(
    key = PathIconKey("icons/filled_build_status.svg", ProjectBuildHeader::class.java),
    contentDescription = "Build Status",
    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
  )
}
