package com.jetbrains.rider.diagnostics

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project


interface SpecialPathsProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<SpecialPathsProvider>("com.intellij.rider.diagnostics.specialPathsProvider")
  }

  fun collectPaths(project: Project?): List<SpecialPathEntry>
}